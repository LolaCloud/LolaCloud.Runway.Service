package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.exception.InvalidOperationException;
import com.miranda.luna.runway.domain.exception.ResourceNotFoundException;
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
import com.miranda.luna.runway.infrastructure.web.dto.AddRouteRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ExposePlatformRequest;
import com.miranda.luna.runway.infrastructure.web.dto.ExposePlatformResponse;
import com.miranda.luna.runway.infrastructure.web.dto.ExposedServiceDto;
import com.miranda.luna.runway.infrastructure.web.dto.IngressRouteRequest;
import com.miranda.luna.runway.infrastructure.web.dto.PublishedRouteDto;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelConnectorDto;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelRequest;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TunnelService {

    private static final Logger logger = LoggerFactory.getLogger(TunnelService.class);

    private final EdgeTunnelRepositoryPort tunnelRepository;
    private final CloudflareAccountRepositoryPort accountRepository;
    private final DnsRecordRepositoryPort recordRepository;
    private final CloudflareClientPort cloudflareClient;
    private final ObjectMapper objectMapper;
    private final RunwayDataSyncService runwayDataSyncService;
    private final EdgeStatusService edgeStatusService;

    public TunnelService(
            EdgeTunnelRepositoryPort tunnelRepository,
            CloudflareAccountRepositoryPort accountRepository,
            DnsRecordRepositoryPort recordRepository,
            CloudflareClientPort cloudflareClient,
            ObjectMapper objectMapper,
            RunwayDataSyncService runwayDataSyncService,
            EdgeStatusService edgeStatusService
    ) {
        this.tunnelRepository = tunnelRepository;
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
        this.cloudflareClient = cloudflareClient;
        this.objectMapper = objectMapper;
        this.runwayDataSyncService = runwayDataSyncService;
        this.edgeStatusService = edgeStatusService;
    }

    @Transactional(readOnly = true)
    public List<TunnelResponse> listTunnels() {
        return tunnelRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TunnelResponse getTunnelById(String idOrTunnelId) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account != null && !isDummyOrTestCredentials(account.getEmail(), account.getGlobalApiKey())) {
            try {
                String cfAccountId = (account.getAccountId() != null && !account.getAccountId().isBlank())
                        ? account.getAccountId()
                        : account.getCfAccountId();

                CloudflareTunnelDto cfTunnel = cloudflareClient.getTunnel(
                        account.getEmail(),
                        account.getGlobalApiKey(),
                        cfAccountId,
                        tunnel.getTunnelId()
                );
                if (cfTunnel != null) {
                    String updatedConfigJson = serializeRichConfig(cfTunnel, tunnel);
                    EdgeTunnel updated = new EdgeTunnel(
                            tunnel.getId(),
                            tunnel.getAccountId(),
                            tunnel.getTunnelId(),
                            cfTunnel.name() != null ? cfTunnel.name() : tunnel.getName(),
                            cfTunnel.status() != null ? cfTunnel.status() : tunnel.getStatus(),
                            updatedConfigJson,
                            tunnel.getCreatedAt()
                    );
                    tunnelRepository.save(updated);
                    return toResponse(updated);
                }
            } catch (Exception e) {
                logger.warn("Could not fetch live Cloudflare status for tunnel {}: {}", tunnel.getTunnelId(), e.getMessage());
            }
        }

        return toResponse(tunnel);
    }

    @Transactional
    public TunnelResponse syncTunnel(String idOrTunnelId) {
        return getTunnelById(idOrTunnelId);
    }

    @Transactional(readOnly = true)
    public String getTunnelToken(String idOrTunnelId) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account == null) {
            account = accountRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active Cloudflare account configured"));
        }

        return cloudflareClient.getTunnelToken(
                account.getAdminEmail(),
                account.getApiKey(),
                account.getCfAccountId(),
                tunnel.getTunnelId()
        );
    }

    @Transactional
    public TunnelResponse createTunnel(TunnelRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidOperationException("Tunnel name is required");
        }

        CloudflareAccount account = resolveAccount(request.accountId());
        if (account == null) {
            account = accountRepository.findAll().stream().findFirst().orElse(null);
        }
        UUID accountId = account != null ? account.getId() : request.accountId();
        String accountName = account != null ? account.getName() : "Default Account";

        String email = account != null ? account.getEmail() : "operator@lolacloud.internal";
        String apiKey = account != null ? account.getGlobalApiKey() : "abcdef0123456789abcdef0123456789";
        String cfAccountId = account != null ? (account.getAccountId() != null && !account.getAccountId().isBlank() ? account.getAccountId() : account.getCfAccountId()) : null;

        CloudflareTunnelDto cfTunnel = cloudflareClient.createTunnel(
                email,
                apiKey,
                cfAccountId,
                request.name().trim()
        );

        String tunnelId = (cfTunnel != null && cfTunnel.tunnelId() != null && !cfTunnel.tunnelId().isBlank())
                ? cfTunnel.tunnelId()
                : UUID.randomUUID().toString();

        boolean hasExplicitRoute = request.hostname() != null && !request.hostname().isBlank();
        List<PublishedRouteDto> initialRoutes = List.of();
        String cleanHostname = null;

        if (hasExplicitRoute) {
            cleanHostname = request.hostname().trim();
            String cleanTarget = request.targetService() != null && !request.targetService().isBlank()
                    ? request.targetService().trim()
                    : "http://localhost:5173";
            initialRoutes = List.of(
                    new PublishedRouteDto(cleanHostname, "*", cleanTarget, cleanTarget, 0)
            );
        }

        String catchAllRule = (cfTunnel != null && cfTunnel.catchAllRule() != null && !cfTunnel.catchAllRule().isBlank())
                ? cfTunnel.catchAllRule()
                : "http_status:404";

        TunnelConfigData configData = new TunnelConfigData("cloudflared", "--", catchAllRule, List.of(), initialRoutes);
        String configJson = serializeConfig(configData);

        String tunnelStatus = (cfTunnel != null && cfTunnel.status() != null && !cfTunnel.status().isBlank())
                ? cfTunnel.status()
                : "INACTIVE";

        EdgeTunnel tunnel = EdgeTunnel.create(accountId, tunnelId, request.name().trim(), tunnelStatus, configJson);
        EdgeTunnel saved = tunnelRepository.save(tunnel);

        // Auto-create CNAME record and push configuration for this tunnel only if an explicit route/hostname was provided
        if (hasExplicitRoute && cleanHostname != null) {
            if (account != null) {
                try {
                    cloudflareClient.saveTunnelConfiguration(
                            account.getEmail(),
                            account.getGlobalApiKey(),
                            cfAccountId != null ? cfAccountId : account.getAccountId(),
                            tunnelId,
                            initialRoutes,
                            catchAllRule
                    );
                } catch (Exception e) {
                    logger.warn("Failed to update tunnel configuration on Cloudflare for tunnel {}: {}", tunnelId, e.getMessage());
                }
            }

            try {
                String zoneId = "zone-default";
                String zoneName = "lolacloud.internal";
                if (account != null) {
                    List<DnsZone> zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
                    for (DnsZone z : zones) {
                        if (cleanHostname.endsWith("." + z.name()) || cleanHostname.equalsIgnoreCase(z.name())) {
                            zoneId = z.id();
                            zoneName = z.name();
                            break;
                        }
                    }
                    if ("zone-default".equals(zoneId) && !zones.isEmpty()) {
                        zoneId = zones.get(0).id();
                        zoneName = zones.get(0).name();
                    }
                }

                String cnameContent = tunnelId + ".cfargotunnel.com";
                String cfRecId = null;
                if (account != null) {
                    cfRecId = cloudflareClient.createDnsRecord(
                            account.getEmail(),
                            account.getGlobalApiKey(),
                            zoneId,
                            "CNAME",
                            cleanHostname,
                            cnameContent,
                            1,
                            true
                    );
                }

                DnsRecord cname = DnsRecord.create(
                        accountId,
                        zoneId,
                        zoneName,
                        cleanHostname,
                        "CNAME",
                        cnameContent,
                        1,
                        true,
                        false,
                        cfRecId
                );
                recordRepository.save(cname);
            } catch (Exception e) {
                logger.warn("Failed to auto-create CNAME for tunnel {}: {}", tunnelId, e.getMessage());
            }
        }

        return toResponse(saved);
    }

    @Transactional
    public TunnelResponse addRoute(String idOrTunnelId, AddRouteRequest routeRequest) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        if (routeRequest == null || routeRequest.hostname() == null || routeRequest.hostname().isBlank()) {
            throw new InvalidOperationException("Hostname is required");
        }
        String effectiveService = routeRequest.getEffectiveService();
        if (effectiveService.isBlank()) {
            throw new InvalidOperationException("Service URL is required");
        }

        String cleanHostname = routeRequest.hostname().trim();
        String cleanPath = routeRequest.getEffectivePath();

        TunnelConfigData currentConfig = deserializeConfig(tunnel.getConfigJson());
        List<PublishedRouteDto> routes = new ArrayList<>(currentConfig.routes());

        int existingIdx = -1;
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).hostname().equalsIgnoreCase(cleanHostname)) {
                existingIdx = i;
                break;
            }
        }

        PublishedRouteDto newRoute = new PublishedRouteDto(cleanHostname, cleanPath, effectiveService, effectiveService, 0);
        if (existingIdx >= 0) {
            routes.set(existingIdx, newRoute);
        } else {
            routes.add(newRoute);
        }

        TunnelConfigData updatedConfigData = new TunnelConfigData(
                currentConfig.tunnelType(),
                currentConfig.uptime(),
                currentConfig.catchAllRule(),
                currentConfig.connectors(),
                routes
        );

        // Push configuration and create DNS CNAME record if account is available
        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account != null) {
            cloudflareClient.saveTunnelConfiguration(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    account.getAccountId(),
                    tunnel.getTunnelId(),
                    routes,
                    currentConfig.catchAllRule()
            );

            try {
                List<DnsZone> zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
                DnsZone targetZone = null;
                for (DnsZone z : zones) {
                    if (cleanHostname.endsWith("." + z.name()) || cleanHostname.equalsIgnoreCase(z.name())) {
                        targetZone = z;
                        break;
                    }
                }
                if (targetZone == null && !zones.isEmpty()) {
                    targetZone = zones.get(0);
                }

                if (targetZone != null) {
                    String cnameTarget = tunnel.getTunnelId() + ".cfargotunnel.com";
                    String cfRecId = cloudflareClient.createDnsRecord(
                            account.getEmail(),
                            account.getGlobalApiKey(),
                            targetZone.id(),
                            "CNAME",
                            cleanHostname,
                            cnameTarget,
                            1,
                            true
                    );

                    DnsRecord cnameRecord = DnsRecord.create(
                            account.getId(),
                            targetZone.id(),
                            targetZone.name(),
                            cleanHostname,
                            "CNAME",
                            cnameTarget,
                            1,
                            true,
                            false,
                            cfRecId
                    );
                    recordRepository.save(cnameRecord);
                }
            } catch (Exception e) {
                logger.warn("Failed to create DNS CNAME for route {}: {}", cleanHostname, e.getMessage());
            }
        }

        String updatedConfigJson = serializeConfig(updatedConfigData);
        EdgeTunnel updated = tunnel.withConfigJson(updatedConfigJson);
        EdgeTunnel saved = tunnelRepository.save(updated);
        return toResponse(saved);
    }

    @Transactional
    public TunnelResponse addIngressRoute(String idOrTunnelId, IngressRouteRequest routeRequest) {
        return addRoute(idOrTunnelId, new AddRouteRequest(
                routeRequest.hostname(),
                "*",
                routeRequest.serviceUrl(),
                routeRequest.serviceUrl()
        ));
    }

    @Transactional
    public TunnelResponse deleteRoute(String idOrTunnelId, int routeIndex) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        TunnelConfigData currentConfig = deserializeConfig(tunnel.getConfigJson());
        List<PublishedRouteDto> routes = new ArrayList<>(currentConfig.routes());

        if (routeIndex < 0 || routeIndex >= routes.size()) {
            throw new InvalidOperationException("Invalid route index: " + routeIndex);
        }

        PublishedRouteDto routeToDelete = routes.get(routeIndex);
        routes.remove(routeIndex);

        TunnelConfigData updatedConfigData = new TunnelConfigData(
                currentConfig.tunnelType(),
                currentConfig.uptime(),
                currentConfig.catchAllRule(),
                currentConfig.connectors(),
                routes
        );

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account != null) {
            cloudflareClient.saveTunnelConfiguration(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    account.getAccountId(),
                    tunnel.getTunnelId(),
                    routes,
                    currentConfig.catchAllRule()
            );
        }

        if (routeToDelete != null && routeToDelete.hostname() != null) {
            deleteRouteDnsRecord(account, routeToDelete.hostname());
        }

        String updatedConfigJson = serializeConfig(updatedConfigData);
        EdgeTunnel updated = tunnel.withConfigJson(updatedConfigJson);
        EdgeTunnel saved = tunnelRepository.save(updated);
        return toResponse(saved);
    }

    @Transactional
    public TunnelResponse deleteRouteByHostname(String idOrTunnelId, String hostname) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        if (hostname == null || hostname.isBlank()) {
            throw new InvalidOperationException("Hostname is required");
        }

        String cleanHostname = hostname.trim();
        TunnelConfigData currentConfig = deserializeConfig(tunnel.getConfigJson());
        List<PublishedRouteDto> routes = new ArrayList<>(currentConfig.routes());
        routes.removeIf(r -> r.hostname().equalsIgnoreCase(cleanHostname));

        TunnelConfigData updatedConfigData = new TunnelConfigData(
                currentConfig.tunnelType(),
                currentConfig.uptime(),
                currentConfig.catchAllRule(),
                currentConfig.connectors(),
                routes
        );

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account != null) {
            cloudflareClient.saveTunnelConfiguration(
                    account.getEmail(),
                    account.getGlobalApiKey(),
                    account.getAccountId(),
                    tunnel.getTunnelId(),
                    routes,
                    currentConfig.catchAllRule()
            );
        }

        deleteRouteDnsRecord(account, cleanHostname);

        String updatedConfigJson = serializeConfig(updatedConfigData);
        EdgeTunnel updated = tunnel.withConfigJson(updatedConfigJson);
        EdgeTunnel saved = tunnelRepository.save(updated);
        return toResponse(saved);
    }

    private void deleteDnsRecordsForHostname(CloudflareAccount account, String zoneId, String zoneName, String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return;
        }

        String cleanHostname = hostname.trim();
        CloudflareAccount effectiveAccount = account != null ? account : resolveAccount(null);

        try {
            // 1. Delete all matching records from local repository and Cloudflare
            List<DnsRecord> localRecords = recordRepository.findAll();
            List<DnsRecord> matchingRecords = localRecords.stream()
                    .filter(r -> cleanHostname.equalsIgnoreCase(r.getName()))
                    .toList();

            boolean deletedFromCf = false;
            for (DnsRecord record : matchingRecords) {
                CloudflareAccount acc = effectiveAccount;
                if (acc == null && record.getAccountId() != null) {
                    acc = resolveAccount(record.getAccountId());
                }

                if (acc != null && record.getCfRecordId() != null && !record.getCfRecordId().isBlank()
                        && record.getZoneId() != null && !record.getZoneId().isBlank()) {
                    try {
                        cloudflareClient.deleteDnsRecord(
                                acc.getEmail(),
                                acc.getGlobalApiKey(),
                                record.getZoneId(),
                                record.getCfRecordId()
                        );
                        deletedFromCf = true;
                    } catch (Exception e) {
                        logger.warn("Failed to delete DNS record {} from Cloudflare: {}", record.getCfRecordId(), e.getMessage());
                    }
                }
                recordRepository.deleteById(record.getId());
                logger.info("Deleted local DNS record {} ({}) for hostname {}", record.getId(), record.getCfRecordId(), cleanHostname);
            }

            // 2. Query Cloudflare API directly to find and delete any existing / duplicate / orphan records on Cloudflare
            if (effectiveAccount != null) {
                String effectiveZoneId = zoneId;
                String effectiveZoneName = zoneName;

                if (effectiveZoneId == null || effectiveZoneId.isBlank()) {
                    try {
                        List<DnsZone> zones = cloudflareClient.listZones(effectiveAccount.getEmail(), effectiveAccount.getGlobalApiKey());
                        for (DnsZone z : zones) {
                            if (cleanHostname.endsWith("." + z.name()) || cleanHostname.equalsIgnoreCase(z.name())) {
                                effectiveZoneId = z.id();
                                effectiveZoneName = z.name();
                                break;
                            }
                        }
                        if (effectiveZoneId == null && !zones.isEmpty()) {
                            effectiveZoneId = zones.get(0).id();
                            effectiveZoneName = zones.get(0).name();
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to list zones while deleting DNS records for hostname {}: {}", cleanHostname, e.getMessage());
                    }
                }

                if (effectiveZoneId != null && !effectiveZoneId.isBlank()) {
                    try {
                        List<DnsRecord> cfRecords = cloudflareClient.listDnsRecords(
                                effectiveAccount.getEmail(),
                                effectiveAccount.getGlobalApiKey(),
                                effectiveZoneId,
                                effectiveZoneName != null ? effectiveZoneName : cleanHostname,
                                effectiveAccount.getId()
                        );
                        for (DnsRecord cfRec : cfRecords) {
                            if (cleanHostname.equalsIgnoreCase(cfRec.getName())) {
                                try {
                                    cloudflareClient.deleteDnsRecord(
                                            effectiveAccount.getEmail(),
                                            effectiveAccount.getGlobalApiKey(),
                                            effectiveZoneId,
                                            cfRec.getCfRecordId()
                                    );
                                    logger.info("Deleted existing Cloudflare DNS record {} ({}) in zone {} for hostname {}",
                                            cfRec.getCfRecordId(), cfRec.getType(), effectiveZoneName, cleanHostname);
                                } catch (Exception e) {
                                    logger.warn("Failed to delete Cloudflare DNS record {}: {}", cfRec.getCfRecordId(), e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to search/delete DNS records from Cloudflare for hostname {}: {}", cleanHostname, e.getMessage());
                    }
                }
            }

            logger.info("DNS record(s) for hostname {} cleanly deleted from Cloudflare and database", cleanHostname);
        } catch (Exception e) {
            logger.error("Error during DNS record cleanup for hostname {}: {}", cleanHostname, e.getMessage(), e);
        }
    }

    private void deleteRouteDnsRecord(CloudflareAccount account, String hostname) {
        deleteDnsRecordsForHostname(account, null, null, hostname);
    }

    @Transactional
    public TunnelResponse deleteIngressRoute(String idOrTunnelId, String hostname) {
        return deleteRouteByHostname(idOrTunnelId, hostname);
    }

    @Transactional
    public List<TunnelResponse> syncTunnels() {
        logger.info("Triggering instant Cloudflare Zero Trust tunnel synchronization");
        runwayDataSyncService.syncAllAccounts();
        return listTunnels();
    }

    @Transactional
    public void deleteTunnel(String idOrTunnelId) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account == null) {
            account = accountRepository.findAll().stream().findFirst().orElse(null);
        }

        String tunnelIdStr = tunnel.getTunnelId();

        // 1. Clean up associated DNS CNAME records from local recordRepository and Cloudflare
        TunnelConfigData configData = deserializeConfig(tunnel.getConfigJson());
        List<String> routeHostnames = configData.routes() != null
                ? configData.routes().stream()
                        .map(PublishedRouteDto::hostname)
                        .filter(h -> h != null && !h.isBlank())
                        .map(String::trim)
                        .toList()
                : List.of();

        List<DnsRecord> allRecords = recordRepository.findAll();
        List<DnsRecord> recordsToDelete = allRecords.stream()
                .filter(r -> {
                    if (r.getContent() != null && r.getContent().contains(tunnelIdStr)) {
                        return true;
                    }
                    if (r.getName() != null && routeHostnames.stream().anyMatch(h -> h.equalsIgnoreCase(r.getName().trim()))) {
                        return true;
                    }
                    return false;
                })
                .toList();

        for (DnsRecord record : recordsToDelete) {
            if (account != null && record.getCfRecordId() != null && !record.getCfRecordId().isBlank()
                    && record.getZoneId() != null && !record.getZoneId().isBlank()) {
                try {
                    cloudflareClient.deleteDnsRecord(
                            account.getEmail(),
                            account.getGlobalApiKey(),
                            record.getZoneId(),
                            record.getCfRecordId()
                    );
                    logger.info("Deleted DNS CNAME record {} ({}) from Cloudflare for tunnel {}",
                            record.getName(), record.getCfRecordId(), tunnelIdStr);
                } catch (Exception e) {
                    logger.warn("Failed to delete DNS record {} from Cloudflare: {}", record.getCfRecordId(), e.getMessage());
                }
            }
            recordRepository.deleteById(record.getId());
            logger.info("Deleted local DNS record {} ({}) for tunnel {}", record.getName(), record.getId(), tunnelIdStr);
        }

        // Also scan Cloudflare zones for any remaining CNAME records pointing to <tunnelId>.cfargotunnel.com
        if (account != null) {
            try {
                List<DnsZone> zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
                for (DnsZone zone : zones) {
                    List<DnsRecord> cfRecords = cloudflareClient.listDnsRecords(
                            account.getEmail(),
                            account.getGlobalApiKey(),
                            zone.id(),
                            zone.name(),
                            account.getId()
                    );
                    for (DnsRecord cfRec : cfRecords) {
                        if ("CNAME".equalsIgnoreCase(cfRec.getType()) && cfRec.getContent() != null && cfRec.getContent().contains(tunnelIdStr)) {
                            cloudflareClient.deleteDnsRecord(
                                    account.getEmail(),
                                    account.getGlobalApiKey(),
                                    zone.id(),
                                    cfRec.getCfRecordId()
                            );
                            logger.info("Deleted orphan DNS CNAME record {} ({}) from Cloudflare zone {}",
                                    cfRec.getName(), cfRec.getCfRecordId(), zone.name());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Cloudflare DNS scan for tunnel {} cleanup failed: {}", tunnelIdStr, e.getMessage());
            }
        }

        // 2. Delete the tunnel from Cloudflare Zero Trust
        if (account != null) {
            try {
                cloudflareClient.deleteTunnel(
                        account.getEmail(),
                        account.getGlobalApiKey(),
                        account.getAccountId(),
                        tunnelIdStr,
                        true
                );
                logger.info("Requested deletion of tunnel {} from Cloudflare Zero Trust", tunnelIdStr);
            } catch (Exception e) {
                logger.warn("Failed to delete tunnel {} from Cloudflare Zero Trust: {}", tunnelIdStr, e.getMessage());
            }
        }

        // 3. Delete tunnel entity from local repository
        tunnelRepository.deleteById(tunnel.getId());
        logger.info("Deleted tunnel {} ({}) from local database", tunnel.getName(), tunnel.getId());
    }

    public SseEmitter streamTunnelLogs(String idOrTunnelId) {
        EdgeTunnel tunnel = findByIdOrTunnelId(idOrTunnelId)
                .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + idOrTunnelId));

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account == null) {
            account = accountRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active Cloudflare account configured"));
        }

        String managementToken = cloudflareClient.getTunnelManagementToken(
                account.getAdminEmail(),
                account.getApiKey(),
                account.getCfAccountId(),
                tunnel.getTunnelId().toString()
        );

        SseEmitter emitter = new SseEmitter(1800000L);

        AtomicReference<Process> processRef = new AtomicReference<>();
        AtomicBoolean isCleanedUp = new AtomicBoolean(false);

        Runnable cleanup = () -> {
            if (isCleanedUp.compareAndSet(false, true)) {
                Process proc = processRef.get();
                if (proc != null && proc.isAlive()) {
                    logger.info("Terminating cloudflared tail process for tunnel {}", tunnel.getTunnelId());
                    proc.destroyForcibly();
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data("{\"status\":\"CONNECTED\",\"message\":\"Listening to live tunnel events from Cloudflare Edge...\"}"));

                ProcessBuilder pb = new ProcessBuilder(
                        "cloudflared",
                        "tail",
                        "--token",
                        managementToken,
                        "--output",
                        "json",
                        tunnel.getTunnelId().toString()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                processRef.set(process);

                if (isCleanedUp.get()) {
                    process.destroyForcibly();
                    return;
                }

                process.onExit().thenAccept(p -> {
                    cleanup.run();
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                });

                // Periodic keep-alive ping to detect disconnected clients promptly
                CompletableFuture.runAsync(() -> {
                    while (!isCleanedUp.get()) {
                        try {
                            Thread.sleep(15000);
                            if (!isCleanedUp.get()) {
                                emitter.send(SseEmitter.event().comment("ping"));
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception ex) {
                            cleanup.run();
                            break;
                        }
                    }
                }, Executors.newVirtualThreadPerTaskExecutor());

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!isCleanedUp.get() && (line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            try {
                                emitter.send(SseEmitter.event().name("log").data(line));
                            } catch (IOException | IllegalStateException e) {
                                logger.debug("SSE client closed connection for tunnel {}: {}", tunnel.getTunnelId(), e.getMessage());
                                break;
                            }
                        }
                    }
                }
            } catch (IOException | IllegalStateException e) {
                logger.debug("Live tunnel log streaming ended for tunnel {}: {}", tunnel.getTunnelId(), e.getMessage());
            } catch (Exception e) {
                logger.warn("Live tunnel log streaming encountered error for tunnel {}: {}", tunnel.getTunnelId(), e.getMessage());
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            } finally {
                cleanup.run();
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }, Executors.newVirtualThreadPerTaskExecutor());

        return emitter;
    }

    private record ServiceDefinition(String name, String subdomain, String targetUrl) {}

    private static final List<ServiceDefinition> PLATFORM_SERVICES = List.of(
            new ServiceDefinition("Lola Console (Frontend)", "console-lola", "http://localhost:5173"),
            new ServiceDefinition("LCI Identity & Auth Backend", "lci-lola", "http://localhost:8080"),
            new ServiceDefinition("Runway Edge Microservice", "runway-lola", "http://localhost:8083"),
            new ServiceDefinition("Relate DBaaS Microservice", "relate-lola", "http://localhost:8084"),
            new ServiceDefinition("Store S3 Control Plane", "store-lola", "http://localhost:8086"),
            new ServiceDefinition("MinIO S3 Engine API", "s3-lola", "http://localhost:9000")
    );

    @Transactional
    public ExposePlatformResponse exposePlatform(ExposePlatformRequest request) {
        String requestedTunnelId = (request != null && request.tunnelId() != null && !request.tunnelId().isBlank())
                ? request.tunnelId().trim()
                : null;
        EdgeTunnel tunnel = resolveTargetTunnel(requestedTunnelId);

        CloudflareAccount account = resolveAccount(tunnel.getAccountId());
        if (account == null) {
            account = accountRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active Cloudflare account configured"));
        }

        String zoneName = (request != null && request.zoneName() != null && !request.zoneName().isBlank())
                ? request.zoneName().trim()
                : "lola-cloud.dev";

        // Read current ingress routes of the tunnel
        TunnelConfigData currentConfig = deserializeConfig(tunnel.getConfigJson());
        List<PublishedRouteDto> routes = new ArrayList<>(currentConfig.routes());

        // Merge / update the 4 ingress routes in config.ingress (pruning any existing routes for these hostnames to prevent duplicates)
        for (ServiceDefinition def : PLATFORM_SERVICES) {
            String hostname = def.subdomain() + "." + zoneName;
            String targetUrl = def.targetUrl();

            routes.removeIf(r -> r.hostname() != null && r.hostname().equalsIgnoreCase(hostname));
            routes.add(new PublishedRouteDto(hostname, "*", targetUrl, targetUrl, 0));
        }

        // Save updated configuration to Cloudflare
        cloudflareClient.saveTunnelConfiguration(
                account.getEmail(),
                account.getGlobalApiKey(),
                account.getAccountId(),
                tunnel.getTunnelId(),
                routes,
                currentConfig.catchAllRule()
        );

        // Provision DNS CNAME records for each of the 4 platform hostnames
        List<DnsZone> zones = List.of();
        try {
            zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
        } catch (Exception e) {
            logger.warn("Failed to list zones from Cloudflare: {}", e.getMessage());
        }

        DnsZone targetZone = null;
        for (DnsZone z : zones) {
            if (zoneName.equalsIgnoreCase(z.name()) || zoneName.endsWith("." + z.name())) {
                targetZone = z;
                break;
            }
        }
        String zoneId = targetZone != null ? targetZone.id() : "023e105f4ecef8ad9ca31a8372d0c353";
        String effectiveZoneName = targetZone != null ? targetZone.name() : zoneName;
        String cnameTarget = tunnel.getTunnelId() + ".cfargotunnel.com";

        for (ServiceDefinition def : PLATFORM_SERVICES) {
            String hostname = def.subdomain() + "." + zoneName;

            // Delete any existing old records for this hostname from Cloudflare and database (never duplicate)
            deleteDnsRecordsForHostname(account, zoneId, effectiveZoneName, hostname);

            // Create fresh CNAME record in Cloudflare
            String cfRecId = null;
            try {
                cfRecId = cloudflareClient.createDnsRecord(
                        account.getEmail(),
                        account.getGlobalApiKey(),
                        zoneId,
                        "CNAME",
                        hostname,
                        cnameTarget,
                        1,
                        true
                );
            } catch (Exception e) {
                logger.warn("Failed to create DNS CNAME record for {} in Cloudflare: {}", hostname, e.getMessage());
            }

            // Persist fresh record in recordRepository
            DnsRecord recordToSave = DnsRecord.create(
                    account.getId(),
                    zoneId,
                    effectiveZoneName,
                    hostname,
                    "CNAME",
                    cnameTarget,
                    1,
                    true,
                    false,
                    cfRecId
            );
            recordRepository.save(recordToSave);
        }

        // Update tunnelRepository with updated config JSON
        TunnelConfigData updatedConfigData = new TunnelConfigData(
                currentConfig.tunnelType(),
                currentConfig.uptime(),
                currentConfig.catchAllRule(),
                currentConfig.connectors(),
                routes
        );
        String updatedConfigJson = serializeConfig(updatedConfigData);
        EdgeTunnel updated = tunnel.withConfigJson(updatedConfigJson);
        tunnelRepository.save(updated);

        List<ExposedServiceDto> exposedServices = PLATFORM_SERVICES.stream()
                .map(s -> {
                    String hostname = s.subdomain() + "." + zoneName;
                    return new ExposedServiceDto(
                            s.name(),
                            s.subdomain(),
                            hostname,
                            s.targetUrl(),
                            "https://" + hostname,
                            "ACTIVE"
                    );
                })
                .toList();

        return new ExposePlatformResponse(
                tunnel.getTunnelId(),
                tunnel.getName(),
                zoneName,
                exposedServices,
                "Platform successfully exposed to public internet via Cloudflare Zero Trust Tunnel"
        );
    }

    @Transactional(readOnly = true)
    public ExposePlatformResponse getPlatformExposeStatus() {
        EdgeTunnel tunnel;
        try {
            tunnel = resolveTargetTunnel(null);
        } catch (ResourceNotFoundException e) {
            List<ExposedServiceDto> unconfiguredServices = PLATFORM_SERVICES.stream()
                    .map(s -> new ExposedServiceDto(
                            s.name(),
                            s.subdomain(),
                            s.subdomain() + ".lola-cloud.dev",
                            s.targetUrl(),
                            "https://" + s.subdomain() + ".lola-cloud.dev",
                            "NOT_CONFIGURED"
                    ))
                    .toList();
            return new ExposePlatformResponse(
                    null,
                    null,
                    "lola-cloud.dev",
                    unconfiguredServices,
                    "No Cloudflare Zero Trust tunnel configured"
            );
        }

        TunnelConfigData currentConfig = deserializeConfig(tunnel.getConfigJson());
        List<PublishedRouteDto> routes = currentConfig.routes();

        String zoneName = "lola-cloud.dev";
        for (PublishedRouteDto r : routes) {
            if (r.hostname() != null) {
                String h = r.hostname().toLowerCase();
                for (ServiceDefinition def : PLATFORM_SERVICES) {
                    String prefix = def.subdomain() + ".";
                    if (h.startsWith(prefix) && h.length() > prefix.length()) {
                        zoneName = h.substring(prefix.length());
                        break;
                    }
                }
            }
        }

        final String effectiveZoneName = zoneName;
        boolean allActive = true;
        boolean anyActive = false;
        List<ExposedServiceDto> services = new ArrayList<>();

        for (ServiceDefinition def : PLATFORM_SERVICES) {
            String hostname = def.subdomain() + "." + effectiveZoneName;
            String publicUrl = "https://" + hostname;

            boolean routeConfigured = routes.stream()
                    .anyMatch(r -> r.hostname() != null && r.hostname().equalsIgnoreCase(hostname));

            String status;
            if (routeConfigured) {
                status = "ACTIVE";
                anyActive = true;
            } else {
                status = "NOT_CONFIGURED";
                allActive = false;
            }

            services.add(new ExposedServiceDto(
                    def.name(),
                    def.subdomain(),
                    hostname,
                    def.targetUrl(),
                    publicUrl,
                    status
            ));
        }

        String message;
        if (allActive) {
            message = "Lola platform is fully exposed to the public internet";
        } else if (anyActive) {
            message = "Lola platform is partially exposed";
        } else {
            message = "Lola platform is not exposed";
        }

        return new ExposePlatformResponse(
                tunnel.getTunnelId(),
                tunnel.getName(),
                effectiveZoneName,
                services,
                message
        );
    }

    private EdgeTunnel resolveTargetTunnel(String requestedTunnelId) {
        if (requestedTunnelId != null && !requestedTunnelId.isBlank()) {
            return findByIdOrTunnelId(requestedTunnelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tunnel not found: " + requestedTunnelId));
        }

        // 1. Detect local host server tunnel
        String localTunnelId = edgeStatusService.detectLocalTunnelId();
        if (localTunnelId != null && !localTunnelId.isBlank()) {
            Optional<EdgeTunnel> opt = findByIdOrTunnelId(localTunnelId);
            if (opt.isPresent()) {
                return opt.get();
            }
        }

        // 2. Fallback to Kali tunnel id or name
        Optional<EdgeTunnel> kaliOpt = findByIdOrTunnelId("08e01514-ae58-4592-9a22-57781b8f20ad");
        if (kaliOpt.isPresent()) {
            return kaliOpt.get();
        }
        kaliOpt = findByIdOrTunnelId("kali");
        if (kaliOpt.isPresent()) {
            return kaliOpt.get();
        }

        // 3. Fallback to first healthy tunnel or first available tunnel
        List<EdgeTunnel> tunnels = tunnelRepository.findAll();
        return tunnels.stream()
                .filter(t -> "HEALTHY".equalsIgnoreCase(t.getStatus()))
                .findFirst()
                .or(() -> tunnels.stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("No Edge Tunnel found to expose platform"));
    }

    private Optional<EdgeTunnel> findByIdOrTunnelId(String idOrTunnelId) {
        if (idOrTunnelId == null || idOrTunnelId.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(idOrTunnelId.trim());
            Optional<EdgeTunnel> byId = tunnelRepository.findById(uuid);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        Optional<EdgeTunnel> byTunnelId = tunnelRepository.findByTunnelId(idOrTunnelId.trim());
        if (byTunnelId.isPresent()) {
            return byTunnelId;
        }
        return tunnelRepository.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(idOrTunnelId.trim()))
                .findFirst();
    }

    private CloudflareAccount resolveAccount(UUID accountId) {
        if (accountId != null) {
            Optional<CloudflareAccount> opt = accountRepository.findById(accountId);
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        List<CloudflareAccount> all = accountRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    public record TunnelConfigData(
            String tunnelType,
            String uptime,
            String catchAllRule,
            List<TunnelConnectorDto> connectors,
            List<PublishedRouteDto> routes
    ) {}

    private TunnelConfigData deserializeConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new TunnelConfigData("cloudflared", "--", "http_status:404", List.of(), List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            String tunnelType = root.path("tunnelType").asText("cloudflared");
            String uptime = root.path("uptime").asText("--");
            String catchAll = root.path("catchAllRule").asText("http_status:404");

            List<TunnelConnectorDto> connectors = new ArrayList<>();
            JsonNode connNode = root.path("connectors");
            if (connNode.isArray()) {
                for (JsonNode c : connNode) {
                    connectors.add(new TunnelConnectorDto(
                            c.path("connectorId").asText(),
                            c.path("hostname").asText(),
                            c.path("dataCenter").asText(),
                            c.path("originIp").asText(),
                            c.path("version").asText(),
                            c.path("platform").asText("linux_amd64"),
                            c.path("status").asText("Connected"),
                            c.path("isHostServer").asBoolean(false)
                    ));
                }
            }

            List<PublishedRouteDto> routes = new ArrayList<>();
            JsonNode routesNode = root.path("routes");
            if (routesNode.isArray()) {
                for (JsonNode r : routesNode) {
                    String hostname = r.path("hostname").asText();
                    String path = r.path("path").asText("*");
                    String service = r.hasNonNull("service") ? r.path("service").asText() : r.path("serviceUrl").asText();
                    String serviceUrl = r.hasNonNull("serviceUrl") ? r.path("serviceUrl").asText() : service;
                    int originConfigs = r.path("originConfigurations").asInt(0);
                    routes.add(new PublishedRouteDto(hostname, path, service, serviceUrl, originConfigs));
                }
            }

            return new TunnelConfigData(tunnelType, uptime, catchAll, connectors, routes);
        } catch (Exception e) {
            logger.warn("Could not deserialize tunnel config: {}", e.getMessage());
            return new TunnelConfigData("cloudflared", "--", "http_status:404", List.of(), List.of());
        }
    }

    private String serializeConfig(TunnelConfigData data) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tunnelType", data.tunnelType() != null ? data.tunnelType() : "cloudflared");
            map.put("uptime", data.uptime() != null ? data.uptime() : "--");
            map.put("catchAllRule", data.catchAllRule() != null ? data.catchAllRule() : "http_status:404");
            map.put("connectors", data.connectors() != null ? data.connectors() : List.of());
            map.put("routes", data.routes() != null ? data.routes() : List.of());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            logger.warn("Failed to serialize tunnel config: {}", e.getMessage());
            return "{\"routes\":[]}";
        }
    }

    private String serializeRichConfig(CloudflareTunnelDto cfTunnel) {
        return serializeRichConfig(cfTunnel, null);
    }

    private String serializeRichConfig(CloudflareTunnelDto cfTunnel, EdgeTunnel existingTunnel) {
        if (cfTunnel == null) {
            return existingTunnel != null && existingTunnel.getConfigJson() != null
                    ? existingTunnel.getConfigJson()
                    : "{\"routes\":[]}";
        }
        List<PublishedRouteDto> routes = cfTunnel.routes();
        String catchAll = cfTunnel.catchAllRule();
        if ((routes == null || routes.isEmpty()) && existingTunnel != null) {
            TunnelConfigData existingData = deserializeConfig(existingTunnel.getConfigJson());
            if (existingData.routes() != null && !existingData.routes().isEmpty()) {
                routes = existingData.routes();
            }
            if (catchAll == null || catchAll.isBlank() || "http_status:404".equals(catchAll)) {
                if (existingData.catchAllRule() != null && !existingData.catchAllRule().isBlank()) {
                    catchAll = existingData.catchAllRule();
                }
            }
        }
        return serializeConfig(new TunnelConfigData(
                cfTunnel.tunnelType() != null ? cfTunnel.tunnelType() : "cloudflared",
                cfTunnel.uptime() != null ? cfTunnel.uptime() : "--",
                catchAll != null ? catchAll : "http_status:404",
                cfTunnel.connectors() != null ? cfTunnel.connectors() : List.of(),
                routes != null ? routes : List.of()
        ));
    }

    private boolean isDummyOrTestCredentials(String email, String apiKey) {
        if (email == null || apiKey == null) {
            return true;
        }
        String e = email.trim().toLowerCase();
        String k = apiKey.trim().toLowerCase();
        if (e.isBlank() || k.isBlank()) {
            return true;
        }
        return e.contains("dummy") || e.contains("test") || e.contains("example")
                || e.contains("invalid") || e.contains("operator-sync") || e.endsWith("internal")
                || k.contains("dummy") || k.contains("test") || k.contains("invalid")
                || "abcdef0123456789abcdef0123456789".equals(k)
                || "test-token".equals(k);
    }

    private TunnelResponse toResponse(EdgeTunnel tunnel) {
        TunnelConfigData data = deserializeConfig(tunnel.getConfigJson());
        String accountName = "Default Account";
        if (tunnel.getAccountId() != null) {
            accountName = accountRepository.findById(tunnel.getAccountId())
                    .map(CloudflareAccount::getName)
                    .orElse("Default Account");
        }

        int connectionsCount = !data.connectors().isEmpty()
                ? data.connectors().size()
                : ("HEALTHY".equalsIgnoreCase(tunnel.getStatus()) ? 4 : 0);

        List<IngressRoute> ingressRoutes = data.routes().stream()
                .map(r -> new IngressRoute(r.hostname(), r.serviceUrl() != null ? r.serviceUrl() : r.service()))
                .toList();

        // Host server detection
        String hostName = edgeStatusService.getHostServerName();
        String localTunnelId = edgeStatusService.detectLocalTunnelId();
        String wanIp = edgeStatusService.getCurrentWanIp();

        boolean isHostServer = false;
        if (localTunnelId != null && !localTunnelId.isBlank() && tunnel.getTunnelId() != null
                && tunnel.getTunnelId().equalsIgnoreCase(localTunnelId)) {
            isHostServer = true;
        } else if (hostName != null && !hostName.isBlank() && tunnel.getName() != null
                && tunnel.getName().equalsIgnoreCase(hostName)) {
            isHostServer = true;
        }

        final boolean finalTunnelIsHostServer = isHostServer;
        List<TunnelConnectorDto> updatedConnectors = data.connectors().stream()
                .map(c -> {
                    boolean connIsHost = finalTunnelIsHostServer
                            || (hostName != null && !hostName.isBlank() && c.hostname() != null && c.hostname().equalsIgnoreCase(hostName))
                            || (wanIp != null && !wanIp.isBlank() && c.originIp() != null && c.originIp().equals(wanIp))
                            || Boolean.TRUE.equals(c.isHostServer());
                    return new TunnelConnectorDto(
                            c.connectorId(),
                            c.hostname(),
                            c.dataCenter(),
                            c.originIp(),
                            c.version(),
                            c.platform(),
                            c.status(),
                            connIsHost
                    );
                })
                .toList();

        return new TunnelResponse(
                tunnel.getId(),
                tunnel.getTunnelId(),
                tunnel.getName(),
                tunnel.getAccountId(),
                accountName,
                tunnel.getStatus(),
                data.tunnelType(),
                data.uptime(),
                connectionsCount,
                data.routes().size(),
                updatedConnectors,
                data.routes(),
                ingressRoutes,
                data.catchAllRule(),
                tunnel.getCreatedAt(),
                isHostServer
        );
    }
}
