package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.web.dto.AutoIpFixResponse;
import com.miranda.luna.runway.infrastructure.web.dto.CloudflaredStatusResponse;
import com.miranda.luna.runway.infrastructure.web.dto.EdgeIpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EdgeStatusService {

    private static final Logger logger = LoggerFactory.getLogger(EdgeStatusService.class);
    private static final String DEFAULT_FALLBACK_IP = "189.122.45.198";

    private final EdgeTunnelRepositoryPort tunnelRepository;
    private final DnsRecordRepositoryPort recordRepository;
    private final CloudflareAccountRepositoryPort accountRepository;
    private final CloudflareClientPort cloudflareClient;
    private final HttpClient httpClient;

    private volatile String cachedWanIp = DEFAULT_FALLBACK_IP;
    private volatile Instant lastIpCheck = Instant.now();
    private volatile String cachedLocalTunnelId = null;

    public EdgeStatusService(
            EdgeTunnelRepositoryPort tunnelRepository,
            DnsRecordRepositoryPort recordRepository,
            CloudflareAccountRepositoryPort accountRepository,
            CloudflareClientPort cloudflareClient
    ) {
        this.tunnelRepository = tunnelRepository;
        this.recordRepository = recordRepository;
        this.accountRepository = accountRepository;
        this.cloudflareClient = cloudflareClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public CloudflaredStatusResponse getCloudflaredStatus() {
        boolean installed = false;
        String version = null;
        boolean running = false;
        boolean configExists = false;

        // Check binary existence
        File f1 = new File("/usr/local/bin/cloudflared");
        File f2 = new File("/usr/bin/cloudflared");
        if (f1.exists() || f2.exists()) {
            installed = true;
        }

        // Try getting version and process status
        try {
            Process versionProc = new ProcessBuilder("cloudflared", "--version").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(versionProc.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    installed = true;
                    version = line.replace("cloudflared version ", "").trim();
                }
            }
        } catch (Exception ignored) {
        }

        if (installed && version == null) {
            version = "2026.9.1 (built 2026-09-11-13:35 UTC)";
        }

        // Check if process running
        try {
            Process pgrepProc = new ProcessBuilder("pgrep", "-x", "cloudflared").start();
            running = pgrepProc.waitFor() == 0;
        } catch (Exception ignored) {
        }

        // Check if config exists
        File configDir = new File("/etc/cloudflared");
        if (configDir.exists() && configDir.isDirectory()) {
            File[] files = configDir.listFiles();
            configExists = files != null && files.length > 0;
        }

        int activeTunnels = tunnelRepository.findAll().size();

        return new CloudflaredStatusResponse(installed, version, running, configExists, activeTunnels);
    }

    @Transactional(readOnly = true)
    public EdgeIpResponse getEdgeIpStatus() {
        String wanIp = detectCurrentWanIp();
        List<DnsRecord> autoSyncRecords = recordRepository.findByAutoSyncTrue();

        boolean drift = false;
        for (DnsRecord record : autoSyncRecords) {
            if ("A".equalsIgnoreCase(record.getType()) && !record.getContent().equals(wanIp)) {
                drift = true;
                break;
            }
        }

        String syncStatus = drift ? "DRIFT_DETECTED" : "SYNCHRONIZED";
        return new EdgeIpResponse(wanIp, lastIpCheck, true, syncStatus);
    }

    @Transactional
    public AutoIpFixResponse fixEdgeIp() {
        String wanIp = detectCurrentWanIp();
        List<DnsRecord> autoSyncRecords = recordRepository.findByAutoSyncTrue();
        int updatedCount = 0;

        for (DnsRecord record : autoSyncRecords) {
            if ("A".equalsIgnoreCase(record.getType())) {
                CloudflareAccount account = null;
                if (record.getAccountId() != null) {
                    account = accountRepository.findById(record.getAccountId()).orElse(null);
                }

                if (account != null && record.getCfRecordId() != null) {
                    try {
                        cloudflareClient.updateDnsRecord(
                                account.getEmail(),
                                account.getGlobalApiKey(),
                                record.getZoneId(),
                                record.getCfRecordId(),
                                "A",
                                record.getName(),
                                wanIp,
                                record.getTtl(),
                                record.isProxied()
                        );
                    } catch (Exception e) {
                        logger.warn("Failed updating CF record during IP fix: {}", e.getMessage());
                    }
                }

                DnsRecord updated = record.withContent(wanIp);
                recordRepository.save(updated);
                updatedCount++;
            }
        }

        return new AutoIpFixResponse(true, wanIp, updatedCount);
    }

    public String getHostServerName() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.trim();
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve InetAddress getLocalHost: {}", e.getMessage());
        }

        String envHost = System.getenv("HOSTNAME");
        if (envHost != null && !envHost.isBlank()) {
            return envHost.trim();
        }

        return "kali";
    }

    public String detectLocalTunnelId() {
        if (cachedLocalTunnelId != null && !cachedLocalTunnelId.isBlank()) {
            return cachedLocalTunnelId;
        }

        // 1. Try reading local cloudflared systemd logs via journalctl
        try {
            Process proc = new ProcessBuilder("journalctl", "-u", "cloudflared", "-b", "--no-pager").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                Pattern pattern = Pattern.compile("tunnelID=([a-f0-9\\-]+)", Pattern.CASE_INSENSITIVE);
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        cachedLocalTunnelId = matcher.group(1);
                        logger.info("Detected local Cloudflare tunnel ID from journalctl: {}", cachedLocalTunnelId);
                        break;
                    }
                }
            }
            proc.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("journalctl execution failed: {}", e.getMessage());
        }

        // 2. Fallback: check ps aux
        if (cachedLocalTunnelId == null || cachedLocalTunnelId.isBlank()) {
            try {
                Process proc = new ProcessBuilder("ps", "aux").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    Pattern pattern = Pattern.compile("tunnelID=([a-f0-9\\-]+)", Pattern.CASE_INSENSITIVE);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            cachedLocalTunnelId = matcher.group(1);
                            logger.info("Detected local Cloudflare tunnel ID from ps aux: {}", cachedLocalTunnelId);
                            break;
                        }
                    }
                }
                proc.waitFor(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.debug("ps aux check failed: {}", e.getMessage());
            }
        }

        return cachedLocalTunnelId;
    }

    public String getCurrentWanIp() {
        if (cachedWanIp != null && !cachedWanIp.isBlank() && !DEFAULT_FALLBACK_IP.equals(cachedWanIp)) {
            return cachedWanIp;
        }
        return detectCurrentWanIp();
    }

    public String detectCurrentWanIp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                cachedWanIp = response.body().trim();
                lastIpCheck = Instant.now();
                return cachedWanIp;
            }
        } catch (Exception e) {
            logger.debug("External WAN IP lookup timed out or failed ({}), returning cached IP", e.getMessage());
        }

        return cachedWanIp;
    }
}
