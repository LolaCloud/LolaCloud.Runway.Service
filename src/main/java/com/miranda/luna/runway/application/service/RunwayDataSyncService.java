package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.DnsZone;
import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.model.IngressRoute;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareTunnelDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RunwayDataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(RunwayDataSyncService.class);

    private final CloudflareAccountRepositoryPort accountRepository;
    private final DnsRecordRepositoryPort recordRepository;
    private final EdgeTunnelRepositoryPort tunnelRepository;
    private final CloudflareClientPort cloudflareClient;
    private final EdgeStatusService edgeStatusService;
    private final ObjectMapper objectMapper;

    public RunwayDataSyncService(
            CloudflareAccountRepositoryPort accountRepository,
            DnsRecordRepositoryPort recordRepository,
            EdgeTunnelRepositoryPort tunnelRepository,
            CloudflareClientPort cloudflareClient,
            EdgeStatusService edgeStatusService
    ) {
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
        this.tunnelRepository = tunnelRepository;
        this.cloudflareClient = cloudflareClient;
        this.edgeStatusService = edgeStatusService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Immediately synchronizes zones, DNS records of all types (A, AAAA, CNAME, MX, TXT, SPF, etc.),
     * and Zero Trust tunnels for a specific Cloudflare account into PostgreSQL.
     * Preserves existing 'auto_sync' settings for records.
     */
    /**
     * Synchronizes zones and DNS records (A, AAAA, CNAME, etc.) from Cloudflare for an account
     * without modifying or pruning edge tunnels.
     */
    @Transactional
    public void syncDnsRecordsForAccount(CloudflareAccount account) {
        if (account == null) {
            return;
        }

        List<DnsZone> zones = List.of();
        try {
            zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
        } catch (Exception e) {
            logger.warn("Failed fetching zones for account {}: {}", account.getEmail(), e.getMessage());
        }

        for (DnsZone zone : zones) {
            try {
                List<DnsRecord> fetchedRecords = cloudflareClient.listDnsRecords(
                        account.getEmail(),
                        account.getGlobalApiKey(),
                        zone.id(),
                        zone.name(),
                        account.getId()
                );

                Set<String> fetchedRecordIds = fetchedRecords.stream()
                        .map(DnsRecord::getCfRecordId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toSet());

                List<DnsRecord> existingRecords = new ArrayList<>(recordRepository.findByZoneId(zone.id()));

                for (DnsRecord cfRec : fetchedRecords) {
                    Optional<DnsRecord> match = existingRecords.stream()
                            .filter(r -> r != null && ((cfRec.getCfRecordId() != null && !cfRec.getCfRecordId().isBlank()
                                    && cfRec.getCfRecordId().equals(r.getCfRecordId()))
                                    || (r.getName() != null && r.getName().equalsIgnoreCase(cfRec.getName())
                                    && r.getType() != null && r.getType().equalsIgnoreCase(cfRec.getType())
                                    && r.getZoneId() != null && r.getZoneId().equalsIgnoreCase(zone.id()))))
                            .findFirst();

                    if (match.isPresent()) {
                        DnsRecord existing = match.get();
                        DnsRecord updated = new DnsRecord(
                                existing.getId(),
                                account.getId(),
                                zone.id(),
                                zone.name() != null ? zone.name() : existing.getZoneName(),
                                cfRec.getName(),
                                cfRec.getType(),
                                cfRec.getContent(),
                                cfRec.getTtl(),
                                cfRec.isProxied(),
                                existing.isAutoSync(),
                                cfRec.getCfRecordId() != null ? cfRec.getCfRecordId() : existing.getCfRecordId(),
                                Instant.now(),
                                existing.getCreatedAt()
                        );
                        DnsRecord saved = recordRepository.save(updated);
                        existingRecords.remove(existing);
                        existingRecords.add(saved != null ? saved : updated);
                    } else {
                        DnsRecord newRecord = new DnsRecord(
                                UUID.randomUUID(),
                                account.getId(),
                                zone.id(),
                                zone.name() != null ? zone.name() : zone.id(),
                                cfRec.getName(),
                                cfRec.getType(),
                                cfRec.getContent(),
                                cfRec.getTtl(),
                                cfRec.isProxied(),
                                false,
                                cfRec.getCfRecordId(),
                                Instant.now(),
                                Instant.now()
                        );
                        DnsRecord saved = recordRepository.save(newRecord);
                        existingRecords.add(saved != null ? saved : newRecord);
                    }
                }

                for (DnsRecord existing : recordRepository.findByZoneId(zone.id())) {
                    if (existing.getCfRecordId() != null && !existing.getCfRecordId().isBlank()
                            && !fetchedRecordIds.contains(existing.getCfRecordId())) {
                        logger.info("Pruning local DNS record '{}' ({}) as it no longer exists on Cloudflare",
                                existing.getName(), existing.getCfRecordId());
                        recordRepository.deleteById(existing.getId());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed syncing DNS records for zone {} in account {}: {}", zone.name(), account.getEmail(), e.getMessage());
            }
        }
    }

    @Transactional
    public void syncAccount(CloudflareAccount account) {
        if (account == null) {
            return;
        }

        logger.info("Starting data sync for account '{}' ({})", account.getName(), account.getEmail());

        // 1. Fetch zones and DNS records from Cloudflare
        syncDnsRecordsForAccount(account);

        // 2. Fetch tunnels from Cloudflare and upsert rich metadata into tunnelRepository
        try {
            List<CloudflareTunnelDto> cfTunnels = cloudflareClient.listTunnels(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    account.getAccountId()
            );

            Set<String> cfTunnelIds = cfTunnels.stream()
                    .map(CloudflareTunnelDto::tunnelId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toSet());

            for (CloudflareTunnelDto cfTunnel : cfTunnels) {
                Optional<EdgeTunnel> existingTunnel = tunnelRepository.findByTunnelId(cfTunnel.tunnelId());
                String routesJson = serializeRichConfig(cfTunnel);

                if (existingTunnel.isPresent()) {
                    EdgeTunnel existing = existingTunnel.get();
                    EdgeTunnel updated = new EdgeTunnel(
                            existing.getId(),
                            account.getId(),
                            cfTunnel.tunnelId(),
                            cfTunnel.name() != null ? cfTunnel.name() : existing.getName(),
                            cfTunnel.status() != null ? cfTunnel.status() : existing.getStatus(),
                            routesJson,
                            existing.getCreatedAt()
                    );
                    tunnelRepository.save(updated);
                } else {
                    EdgeTunnel newTunnel = EdgeTunnel.create(
                            account.getId(),
                            cfTunnel.tunnelId(),
                            cfTunnel.name(),
                            cfTunnel.status() != null ? cfTunnel.status() : "HEALTHY",
                            routesJson
                    );
                    tunnelRepository.save(newTunnel);
                }
            }

            // Prune tunnels: Find all tunnels in tunnelRepository belonging to account.getId().
            // Any tunnel whose tunnelId is NOT in cfTunnelIds must be deleted from tunnelRepository.
            List<EdgeTunnel> localTunnels = tunnelRepository.findByAccountId(account.getId());
            for (EdgeTunnel localTunnel : localTunnels) {
                if (localTunnel.getTunnelId() != null && !cfTunnelIds.contains(localTunnel.getTunnelId())) {
                    logger.info("Pruning local tunnel '{}' ({}) as it no longer exists on Cloudflare",
                            localTunnel.getName(), localTunnel.getTunnelId());
                    tunnelRepository.deleteById(localTunnel.getId());
                }
            }

            // Also prune any orphaned tunnels without accountId that are not in Cloudflare
            for (EdgeTunnel orphan : tunnelRepository.findAll()) {
                if (orphan.getAccountId() == null && orphan.getTunnelId() != null && !cfTunnelIds.contains(orphan.getTunnelId())) {
                    logger.info("Pruning orphaned local tunnel '{}' ({})", orphan.getName(), orphan.getTunnelId());
                    tunnelRepository.deleteById(orphan.getId());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed syncing tunnels for account {}: {}", account.getEmail(), e.getMessage());
        }

        logger.info("Completed data sync for account '{}' ({})", account.getName(), account.getEmail());
    }

    /**
     * Iterates all accounts and synchronizes each.
     */
    @Transactional
    public void syncAllAccounts() {
        List<CloudflareAccount> accounts = accountRepository.findAll();
        logger.debug("Syncing all Cloudflare accounts (total: {})", accounts.size());
        for (CloudflareAccount account : accounts) {
            try {
                syncAccount(account);
            } catch (Exception e) {
                logger.warn("Error synchronizing account {}: {}", account.getEmail(), e.getMessage());
            }
        }
    }

    private volatile CompletableFuture<Void> startupSyncFuture;

    /**
     * Startup sync triggered when application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupSync() {
        logger.info("Runway startup complete: initiating background Cloudflare synchronization...");
        this.startupSyncFuture = CompletableFuture.runAsync(() -> {
            try {
                syncAllAccounts();
                logger.info("Startup Cloudflare synchronization completed successfully.");
            } catch (Exception e) {
                logger.warn("Startup synchronization encountered an issue: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> getStartupSyncFuture() {
        return startupSyncFuture;
    }

    /**
     * Periodic sync job executing syncAllAccounts().
     */
    @Scheduled(fixedDelayString = "${lola.runway.sync.interval-ms:600000}", initialDelay = 60000)
    public void scheduledSync() {
        logger.info("Executing scheduled Cloudflare data synchronization");
        syncAllAccounts();
    }

    /**
     * Periodic DDNS auto-fix job calling edgeStatusService.fixEdgeIp().
     */
    @Scheduled(fixedDelayString = "${lola.runway.ddns.interval-ms:600000}", initialDelay = 60000)
    public void scheduledDdnsAutoFix() {
        logger.info("Executing scheduled DDNS IP auto-fix");
        try {
            edgeStatusService.fixEdgeIp();
        } catch (Exception e) {
            logger.warn("Scheduled DDNS auto-fix failed: {}", e.getMessage());
        }
    }

    private String serializeRichConfig(CloudflareTunnelDto cfTunnel) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tunnelType", cfTunnel.tunnelType() != null ? cfTunnel.tunnelType() : "cloudflared");
            map.put("uptime", cfTunnel.uptime() != null ? cfTunnel.uptime() : "--");
            map.put("catchAllRule", cfTunnel.catchAllRule() != null ? cfTunnel.catchAllRule() : "http_status:404");
            map.put("connectors", cfTunnel.connectors() != null ? cfTunnel.connectors() : List.of());
            map.put("routes", cfTunnel.routes() != null ? cfTunnel.routes() : List.of());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            logger.warn("Failed to serialize rich tunnel config: {}", e.getMessage());
            return "{\"routes\":[]}";
        }
    }
}
