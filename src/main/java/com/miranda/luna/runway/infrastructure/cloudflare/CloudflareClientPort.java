package com.miranda.luna.runway.infrastructure.cloudflare;

import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.DnsZone;
import com.miranda.luna.runway.domain.model.IngressRoute;
import com.miranda.luna.runway.infrastructure.web.dto.PublishedRouteDto;

import java.util.List;
import java.util.UUID;

public interface CloudflareClientPort {
    CloudflareValidationResult validateCredentials(String email, String apiKey);
    List<DnsZone> listZones(String email, String apiKey);
    List<DnsRecord> listDnsRecords(String email, String apiKey, String zoneId, String zoneName, UUID accountId);
    List<CloudflareTunnelDto> listTunnels(String email, String apiKey, String accountId);
    CloudflareTunnelDto getTunnel(String email, String apiKey, String cfAccountId, String tunnelId);
    CloudflareTunnelDto createTunnel(String email, String apiKey, String cfAccountId, String tunnelName);
    String resolveAccountId(String email, String apiKey);
    String createDnsRecord(String email, String apiKey, String zoneId, String type, String name, String content, int ttl, boolean proxied);
    void updateDnsRecord(String email, String apiKey, String zoneId, String cfRecordId, String type, String name, String content, int ttl, boolean proxied);
    void deleteDnsRecord(String email, String apiKey, String zoneId, String cfRecordId);
    void saveTunnelConfiguration(String email, String apiKey, String cfAccountId, String tunnelId, List<IngressRoute> routes);
    void saveTunnelConfiguration(String email, String apiKey, String cfAccountId, String tunnelId, List<PublishedRouteDto> routes, String catchAllRule);
    String getTunnelManagementToken(String email, String apiKey, String cfAccountId, String tunnelId);
    String getTunnelToken(String email, String apiKey, String cfAccountId, String tunnelId);
    void deleteTunnel(String email, String apiKey, String cfAccountId, String tunnelId, Boolean cascade);
}
