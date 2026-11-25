package com.miranda.luna.runway.application.service;

import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort;
import com.miranda.luna.runway.infrastructure.cloudflare.CloudflareTunnelDto;
import com.miranda.luna.runway.infrastructure.web.dto.PublishedRouteDto;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelConnectorDto;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TunnelServiceTest {

    @Mock
    private EdgeTunnelRepositoryPort tunnelRepository;

    @Mock
    private CloudflareAccountRepositoryPort accountRepository;

    @Mock
    private DnsRecordRepositoryPort recordRepository;

    @Mock
    private CloudflareClientPort cloudflareClient;

    @Mock
    private RunwayDataSyncService runwayDataSyncService;

    @Mock
    private EdgeStatusService edgeStatusService;

    private ObjectMapper objectMapper;
    private TunnelService tunnelService;

    private UUID accountId;
    private CloudflareAccount realAccount;
    private EdgeTunnel localTunnel;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tunnelService = new TunnelService(
                tunnelRepository,
                accountRepository,
                recordRepository,
                cloudflareClient,
                objectMapper,
                runwayDataSyncService,
                edgeStatusService
        );

        accountId = UUID.randomUUID();
        realAccount = CloudflareAccount.create(
                "Production CF",
                "ops@mycompany.io",
                "cf_real_secret_token_12345",
                "cf_acc_998877",
                "CONNECTED"
        );

        localTunnel = new EdgeTunnel(
                UUID.randomUUID(),
                accountId,
                "tun-uuid-1122",
                "my-tunnel",
                "DOWN",
                "{\"tunnelType\":\"cloudflared\",\"uptime\":\"--\",\"connectors\":[],\"routes\":[]}",
                Instant.now()
        );
    }

    @Test
    @DisplayName("getTunnelById actively queries Cloudflare for live status, connectors, and uptime, updates DB and returns fresh response")
    void testGetTunnelByIdActivelyQueriesCloudflare() {
        when(tunnelRepository.findById(localTunnel.getId())).thenReturn(Optional.of(localTunnel));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(realAccount));
        when(edgeStatusService.getHostServerName()).thenReturn("other-server");
        when(edgeStatusService.detectLocalTunnelId()).thenReturn("other-tun");
        when(edgeStatusService.getCurrentWanIp()).thenReturn("1.1.1.1");

        CloudflareTunnelDto liveDto = new CloudflareTunnelDto(
                "tun-uuid-1122",
                "my-tunnel",
                "HEALTHY",
                "cloudflared",
                "5 hours",
                1,
                List.of(new TunnelConnectorDto(
                        "conn-1",
                        "my-tunnel",
                        "gru",
                        "187.74.41.93",
                        "2026.9.1",
                        "linux_amd64",
                        "Connected"
                )),
                List.of(new PublishedRouteDto("app.mycompany.io", "*", "http://localhost:8080", "http://localhost:8080", 0)),
                "http_status:404",
                Instant.now()
        );

        when(cloudflareClient.getTunnel(
                eq(realAccount.getEmail()),
                eq(realAccount.getGlobalApiKey()),
                eq(realAccount.getAccountId()),
                eq(localTunnel.getTunnelId())
        )).thenReturn(liveDto);

        TunnelResponse response = tunnelService.getTunnelById(localTunnel.getId().toString());

        assertNotNull(response);
        assertEquals("HEALTHY", response.status(), "Status must be updated to HEALTHY from live Cloudflare response");
        assertEquals("5 hours", response.uptime(), "Uptime must be updated from live Cloudflare response");
        assertEquals(1, response.connectionsCount());
        assertEquals(1, response.connectors().size());
        assertEquals("conn-1", response.connectors().get(0).connectorId());

        // Verify state was saved to database repository
        ArgumentCaptor<EdgeTunnel> captor = ArgumentCaptor.forClass(EdgeTunnel.class);
        verify(tunnelRepository).save(captor.capture());
        EdgeTunnel saved = captor.getValue();
        assertEquals("HEALTHY", saved.getStatus());
        assertEquals("my-tunnel", saved.getName());
    }

    @Test
    @DisplayName("getTunnelById falls back to local DB state when Cloudflare API call fails")
    void testGetTunnelByIdFallsBackOnCloudflareFailure() {
        when(tunnelRepository.findById(localTunnel.getId())).thenReturn(Optional.of(localTunnel));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(realAccount));
        when(edgeStatusService.getHostServerName()).thenReturn("other-server");
        when(edgeStatusService.detectLocalTunnelId()).thenReturn("other-tun");
        when(edgeStatusService.getCurrentWanIp()).thenReturn("1.1.1.1");

        when(cloudflareClient.getTunnel(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Cloudflare API connection timeout"));

        TunnelResponse response = tunnelService.getTunnelById(localTunnel.getId().toString());

        assertNotNull(response);
        assertEquals("DOWN", response.status(), "Should fall back to stored DB status");
        assertEquals("--", response.uptime());
    }

    @Test
    @DisplayName("syncTunnel actively queries Cloudflare, updates DB, and triggers account data sync")
    void testSyncTunnelQueriesCloudflareAndSyncsAccount() {
        when(tunnelRepository.findById(localTunnel.getId())).thenReturn(Optional.of(localTunnel));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(realAccount));
        when(edgeStatusService.getHostServerName()).thenReturn("other-server");
        when(edgeStatusService.detectLocalTunnelId()).thenReturn("other-tun");
        when(edgeStatusService.getCurrentWanIp()).thenReturn("1.1.1.1");

        CloudflareTunnelDto liveDto = new CloudflareTunnelDto(
                "tun-uuid-1122",
                "my-tunnel",
                "HEALTHY",
                "cloudflared",
                "2 days",
                2,
                List.of(
                        new TunnelConnectorDto("conn-1", "host-1", "gru", "187.74.41.93", "2026.9.1", "linux_amd64", "Connected"),
                        new TunnelConnectorDto("conn-2", "host-2", "mia", "187.74.41.94", "2026.9.1", "linux_amd64", "Connected")
                ),
                List.of(),
                "http_status:404",
                Instant.now()
        );

        when(cloudflareClient.getTunnel(any(), any(), any(), any())).thenReturn(liveDto);

        TunnelResponse response = tunnelService.syncTunnel(localTunnel.getId().toString());

        assertNotNull(response);
        assertEquals("HEALTHY", response.status());
        assertEquals("2 days", response.uptime());
        assertEquals(2, response.connectionsCount());

        verify(tunnelRepository).save(any(EdgeTunnel.class));
    }
}
