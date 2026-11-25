package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.DnsRecord;
import com.miranda.luna.runway.domain.model.DnsZone;
import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.model.IngressRoute;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClient;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareTunnelDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunwayDataSyncServiceTest {

    @Mock
    private CloudflareAccountRepositoryPort accountRepository;

    @Mock
    private DnsRecordRepositoryPort recordRepository;

    @Mock
    private EdgeTunnelRepositoryPort tunnelRepository;

    @Mock
    private CloudflareClientPort cloudflareClient;

    @Mock
    private EdgeStatusService edgeStatusService;

    private RunwayDataSyncService dataSyncService;

    private CloudflareAccount testAccount;

    @BeforeEach
    void setUp() {
        dataSyncService = new RunwayDataSyncService(
                accountRepository,
                recordRepository,
                tunnelRepository,
                cloudflareClient,
                edgeStatusService
        );

        testAccount = CloudflareAccount.create(
                "Test Org",
                "test@lola-cloud.dev",
                "0123456789abcdef0123456789abcdef",
                "cf-acc-12345",
                "CONNECTED"
        );
    }

    @Test
    @DisplayName("syncAccount synchronizes all DNS record types and preserves auto_sync for existing records")
    void testSyncAccountPreservesAutoSyncAndSyncsAllTypes() {
        DnsZone zone = new DnsZone("zone-lola", "lola-cloud.dev", "cf-acc-12345", "Test Org", "active", 2);
        when(cloudflareClient.listZones(testAccount.getEmail(), testAccount.getGlobalApiKey()))
                .thenReturn(List.of(zone));

        // Existing A record with autoSync = true (DDNS)
        DnsRecord existingDdnsRecord = new DnsRecord(
                UUID.randomUUID(),
                testAccount.getId(),
                "zone-lola",
                "lola-cloud.dev",
                "lola-cloud.dev",
                "A",
                "1.1.1.1", // old IP
                300,
                true,
                true, // autoSync is TRUE
                "cf-rec-apex-1",
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(86400)
        );

        when(recordRepository.findByZoneId("zone-lola")).thenReturn(List.of(existingDdnsRecord));

        // Cloudflare returns updated A record + AAAA + CNAME + MX + TXT + SPF records
        List<DnsRecord> cloudflareRecords = List.of(
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "lola-cloud.dev", "A", "189.122.45.198", 1, true, false, "cf-rec-apex-1"),
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "ipv6.lola-cloud.dev", "AAAA", "2606:4700::1", 1, true, false, "cf-rec-aaaa-1"),
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "console.lola-cloud.dev", "CNAME", "tunnel.cfargotunnel.com", 1, true, false, "cf-rec-cname-1"),
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "lola-cloud.dev", "MX", "mail.lola-cloud.dev", 3600, false, false, "cf-rec-mx-1"),
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "lola-cloud.dev", "TXT", "v=spf1 ~all", 3600, false, false, "cf-rec-txt-1"),
                DnsRecord.create(testAccount.getId(), "zone-lola", "lola-cloud.dev", "_spf.lola-cloud.dev", "SPF", "v=spf1 ~all", 3600, false, false, "cf-rec-spf-1")
        );

        when(recordRepository.save(any(DnsRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(cloudflareClient.listDnsRecords(testAccount.getEmail(), testAccount.getGlobalApiKey(), "zone-lola", "lola-cloud.dev", testAccount.getId()))
                .thenReturn(cloudflareRecords);

        when(cloudflareClient.listTunnels(anyString(), anyString(), anyString())).thenReturn(List.of());

        dataSyncService.syncAccount(testAccount);

        ArgumentCaptor<DnsRecord> captor = ArgumentCaptor.forClass(DnsRecord.class);
        verify(recordRepository, times(6)).save(captor.capture());

        List<DnsRecord> savedRecords = captor.getAllValues();

        // Check that the existing record was updated with new IP, but autoSync was PRESERVED as true
        DnsRecord updatedApex = savedRecords.stream()
                .filter(r -> "cf-rec-apex-1".equals(r.getCfRecordId()))
                .findFirst()
                .orElseThrow();
        assertEquals("189.122.45.198", updatedApex.getContent());
        assertTrue(updatedApex.isAutoSync(), "auto_sync boolean must be preserved as true for existing DDNS record!");

        // Check that new records have auto_sync = false and none of the types were dropped
        assertEquals(6, savedRecords.size());
        assertTrue(savedRecords.stream().anyMatch(r -> "AAAA".equals(r.getType())));
        assertTrue(savedRecords.stream().anyMatch(r -> "CNAME".equals(r.getType())));
        assertTrue(savedRecords.stream().anyMatch(r -> "MX".equals(r.getType())));
        assertTrue(savedRecords.stream().anyMatch(r -> "TXT".equals(r.getType())));
        assertTrue(savedRecords.stream().anyMatch(r -> "SPF".equals(r.getType())));

        DnsRecord cnameRecord = savedRecords.stream().filter(r -> "CNAME".equals(r.getType())).findFirst().orElseThrow();
        assertFalse(cnameRecord.isAutoSync(), "New records must have auto_sync = false");
    }

    @Test
    @DisplayName("syncAccount synchronizes and upserts Cloudflare tunnels")
    void testSyncAccountUpsertsTunnels() {
        when(cloudflareClient.listZones(anyString(), anyString())).thenReturn(List.of());

        CloudflareTunnelDto tunnelDto = new CloudflareTunnelDto(
                "cf-tun-uuid-1",
                "edge-ingress",
                "HEALTHY",
                4,
                Instant.now(),
                List.of(new IngressRoute("api.lola-cloud.dev", "http://localhost:8080"))
        );

        when(cloudflareClient.listTunnels(testAccount.getEmail(), testAccount.getGlobalApiKey(), testAccount.getAccountId()))
                .thenReturn(List.of(tunnelDto));

        when(tunnelRepository.findByTunnelId("cf-tun-uuid-1")).thenReturn(Optional.empty());

        dataSyncService.syncAccount(testAccount);

        ArgumentCaptor<EdgeTunnel> captor = ArgumentCaptor.forClass(EdgeTunnel.class);
        verify(tunnelRepository).save(captor.capture());

        EdgeTunnel saved = captor.getValue();
        assertEquals("cf-tun-uuid-1", saved.getTunnelId());
        assertEquals("edge-ingress", saved.getName());
        assertEquals("HEALTHY", saved.getStatus());
        assertTrue(saved.getConfigJson().contains("api.lola-cloud.dev"));
    }

    @Test
    @DisplayName("syncAccount prunes local tunnels that no longer exist in Cloudflare")
    void testSyncAccountPrunesTunnelsRemovedFromCloudflare() {
        when(cloudflareClient.listZones(anyString(), anyString())).thenReturn(List.of());
        when(cloudflareClient.listTunnels(testAccount.getEmail(), testAccount.getGlobalApiKey(), testAccount.getAccountId()))
                .thenReturn(List.of()); // Cloudflare returns 0 tunnels

        EdgeTunnel staleTunnel = EdgeTunnel.create(
                testAccount.getId(),
                "stale-tunnel-id",
                "Kali",
                "HEALTHY",
                "{}"
        );
        when(tunnelRepository.findByAccountId(testAccount.getId())).thenReturn(List.of(staleTunnel));

        dataSyncService.syncAccount(testAccount);

        verify(tunnelRepository).deleteById(staleTunnel.getId());
    }

    @Test
    @DisplayName("syncAllAccounts synchronizes each registered Cloudflare account")
    void testSyncAllAccounts() {
        CloudflareAccount acc1 = CloudflareAccount.create("Acc 1", "acc1@lola.dev", "key1", "id1", "CONNECTED");
        CloudflareAccount acc2 = CloudflareAccount.create("Acc 2", "acc2@lola.dev", "key2", "id2", "CONNECTED");

        when(accountRepository.findAll()).thenReturn(List.of(acc1, acc2));
        when(cloudflareClient.listZones(anyString(), anyString())).thenReturn(List.of());
        when(cloudflareClient.listTunnels(anyString(), anyString(), anyString())).thenReturn(List.of());

        dataSyncService.syncAllAccounts();

        verify(cloudflareClient).listZones("acc1@lola.dev", "key1");
        verify(cloudflareClient).listZones("acc2@lola.dev", "key2");
    }

    @Test
    @DisplayName("scheduledDdnsAutoFix triggers fixEdgeIp")
    void testScheduledDdnsAutoFix() {
        dataSyncService.scheduledDdnsAutoFix();
        verify(edgeStatusService).fixEdgeIp();
    }

    @Test
    @DisplayName("CloudflareClient fallback returns realistic records for lola-cloud.dev and benoapp.club")
    void testCloudflareClientFallbackRecords() {
        CloudflareClient client = new CloudflareClient(new ObjectMapper(), "https://api.cloudflare.com/client/v4");

        // Request fallback records for lola-cloud.dev
        List<DnsRecord> lolaRecords = client.listDnsRecords(
                "invalid@lola.dev", "invalid-key-0123456789", "zone-lola-cloud-dev", "lola-cloud.dev", UUID.randomUUID()
        );
        assertNotNull(lolaRecords);
        assertFalse(lolaRecords.isEmpty());
        assertTrue(lolaRecords.stream().anyMatch(r -> "A".equals(r.getType()) && "lola-cloud.dev".equals(r.getName())));
        assertTrue(lolaRecords.stream().anyMatch(r -> "CNAME".equals(r.getType()) && "console.lola-cloud.dev".equals(r.getName())));
        assertTrue(lolaRecords.stream().anyMatch(r -> "TXT".equals(r.getType())));

        // Request fallback records for benoapp.club
        List<DnsRecord> benoRecords = client.listDnsRecords(
                "invalid@lola.dev", "invalid-key-0123456789", "zone-benoapp-club", "benoapp.club", UUID.randomUUID()
        );
        assertNotNull(benoRecords);
        assertFalse(benoRecords.isEmpty());
        assertTrue(benoRecords.stream().anyMatch(r -> "A".equals(r.getType()) && "benoapp.club".equals(r.getName())));
        assertTrue(benoRecords.stream().anyMatch(r -> "CNAME".equals(r.getType()) && "app.benoapp.club".equals(r.getName())));
    }
}
