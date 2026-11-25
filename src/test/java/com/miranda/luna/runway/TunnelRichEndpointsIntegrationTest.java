package com.miranda.luna.runway;

import com.miranda.luna.runway.application.service.TunnelService;
import com.miranda.luna.runway.domain.model.CloudflareAccount;
import com.miranda.luna.runway.domain.model.EdgeTunnel;
import com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort;
import com.miranda.luna.runway.domain.repository.EdgeTunnelRepositoryPort;
import com.miranda.luna.runway.infrastructure.security.LciAuthFilter;
import com.miranda.luna.runway.infrastructure.web.dto.AddRouteRequest;
import com.miranda.luna.runway.infrastructure.web.dto.TunnelResponse;
import com.miranda.luna.runway.infrastructure.web.filter.RequestIdFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TunnelRichEndpointsIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private RequestIdFilter requestIdFilter;

  @Autowired
  private LciAuthFilter lciAuthFilter;

  @Autowired
  private TunnelService tunnelService;

  @Autowired
  private com.miranda.luna.runway.application.service.RunwayDataSyncService runwayDataSyncService;

  @Autowired
  private CloudflareAccountRepositoryPort accountRepository;

  @Autowired
  private com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort recordRepository;

  @Autowired
  private com.miranda.luna.runway.infrastructure.cloudflare.CloudflareClientPort cloudflareClient;

  @Autowired
  private EdgeTunnelRepositoryPort tunnelRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private MockMvc mockMvc;

  @BeforeAll
  void initSuite() {
    if (runwayDataSyncService != null && runwayDataSyncService.getStartupSyncFuture() != null) {
      try {
        runwayDataSyncService.getStartupSyncFuture().get(15, TimeUnit.SECONDS);
      } catch (Exception ignored) {
      }
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .addFilters(requestIdFilter, lciAuthFilter)
        .build();

    CloudflareAccount account;
    if (accountRepository.findAll().isEmpty()) {
      account = accountRepository.save(CloudflareAccount.create(
          "Lola",
          "lucas.miranda.strapasson@gmail.com",
          "dummy_cloudflare_global_api_key_for_tests",
          "10d3982e7f26310ba341e024fc620675",
          "CONNECTED"));
    } else {
      account = accountRepository.findAll().get(0);
    }

    seedTestTunnelsIfMissing(account.getId());
  }

  private void seedTestTunnelsIfMissing(java.util.UUID accountId) {
    String kaliConfig = """
        {
            "tunnelType": "cloudflared",
            "uptime": "1 hour",
            "catchAllRule": "http_status:404",
            "connectors": [
                {
                    "connectorId": "7d597374-1379-45eb-a416-36a7641833ea",
                    "hostname": "kali",
                    "dataCenter": "gru",
                    "originIp": "187.74.41.93",
                    "version": "2026.9.1",
                    "platform": "linux_amd64",
                    "status": "Connected",
                    "isHostServer": true
                }
            ],
            "routes": []
        }
        """;
    var existingKali = tunnelRepository.findByTunnelId("08e01514-ae58-4592-9a22-57781b8f20ad");
    if (existingKali.isPresent()) {
      EdgeTunnel k = existingKali.get();
      tunnelRepository.save(new EdgeTunnel(
          k.getId(),
          accountId,
          k.getTunnelId(),
          "Kali",
          "HEALTHY",
          kaliConfig,
          k.getCreatedAt()));
    } else {
      tunnelRepository.save(EdgeTunnel.create(
          accountId,
          "08e01514-ae58-4592-9a22-57781b8f20ad",
          "Kali",
          "HEALTHY",
          kaliConfig));
    }

    String natanConfig = """
        {
            "tunnelType": "cloudflared",
            "uptime": "--",
            "catchAllRule": "http_status:404",
            "connectors": [],
            "routes": [
                {
                    "hostname": "baixarmusiquinha-natan.lola-cloud.dev",
                    "path": "*",
                    "service": "http://metube_metube_1:8081",
                    "serviceUrl": "http://metube_metube_1:8081",
                    "status": 0
                },
                {
                    "hostname": "umbrel-natan.lola-cloud.dev",
                    "path": "*",
                    "service": "http://umbrel.local",
                    "serviceUrl": "http://umbrel.local",
                    "status": 0
                },
                {
                    "hostname": "ssh.lola-cloud.dev",
                    "path": "*",
                    "service": "ssh://umbrel.local",
                    "serviceUrl": "ssh://umbrel.local",
                    "status": 0
                }
            ]
        }
        """;
    var existingNatan = tunnelRepository.findByTunnelId("27cdd24f-ab4e-4415-a490-557f9976146b");
    if (existingNatan.isPresent()) {
      EdgeTunnel n = existingNatan.get();
      tunnelRepository.save(new EdgeTunnel(
          n.getId(),
          accountId,
          n.getTunnelId(),
          "natan",
          "DOWN",
          natanConfig,
          n.getCreatedAt()));
    } else {
      tunnelRepository.save(EdgeTunnel.create(
          accountId,
          "27cdd24f-ab4e-4415-a490-557f9976146b",
          "natan",
          "DOWN",
          natanConfig));
    }
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels returns rich tunnels list with connectors and published routes")
  void testListTunnelsReturnsRichData() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/runway/tunnels")
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode data = root.path("data");
    assertTrue(data.size() >= 1, "Expected at least 1 tunnel (Kali)");

    // Verify Kali
    boolean foundKali = false;
    boolean foundNatan = false;
    for (JsonNode t : data) {
      String name = t.path("name").asText();
      if ("Kali".equalsIgnoreCase(name)) {
        foundKali = true;
        assertEquals("cloudflared", t.path("tunnelType").asText());
        assertEquals("HEALTHY", t.path("status").asText());
        assertFalse(t.path("uptime").asText().isBlank());
        assertTrue(t.path("connectors").isArray());
        assertFalse(t.path("connectors").isEmpty(), "Kali must contain active connectors");
        JsonNode firstConnector = t.path("connectors").get(0);
        assertNotNull(firstConnector.path("connectorId").asText());
        assertNotNull(firstConnector.path("hostname").asText());
        assertNotNull(firstConnector.path("dataCenter").asText());
        assertNotNull(firstConnector.path("originIp").asText());
        assertEquals("Connected", firstConnector.path("status").asText());
        assertTrue(t.has("isHostServer"), "TunnelResponse must contain isHostServer");
        assertTrue(t.path("isHostServer").asBoolean(), "Kali tunnel should be identified as host server");
        assertTrue(firstConnector.has("isHostServer"), "TunnelConnectorDto must contain isHostServer");
        assertTrue(firstConnector.path("isHostServer").asBoolean(),
            "Kali connector should be identified as host server connector");
      } else if ("natan".equalsIgnoreCase(name)) {
        foundNatan = true;
        assertEquals("cloudflared", t.path("tunnelType").asText());
        assertEquals("DOWN", t.path("status").asText());
        assertTrue(t.has("isHostServer"), "TunnelResponse must contain isHostServer");
        assertFalse(t.path("isHostServer").asBoolean(), "natan tunnel should not be identified as host server");
        assertTrue(t.path("routes").isArray());
        assertTrue(t.path("routes").size() >= 3, "natan must contain published routes");
        assertEquals("http_status:404", t.path("catchAllRule").asText());
      }
    }
    assertTrue(foundKali, "Kali tunnel must be in the response");
    if (foundNatan) {
      assertTrue(tunnelsContainRoute(data, "natan"));
    }
  }

  private boolean tunnelsContainRoute(JsonNode data, String tunnelName) {
    for (JsonNode t : data) {
      if (tunnelName.equalsIgnoreCase(t.path("name").asText())) {
        return t.path("routes").isArray() && !t.path("routes").isEmpty();
      }
    }
    return false;
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id} returns rich tunnel detail")
  void testGetTunnelDetail() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());

    TunnelResponse natan = tunnels.stream()
        .filter(t -> "natan".equalsIgnoreCase(t.name()))
        .findFirst()
        .orElse(tunnels.get(0));

    mockMvc.perform(get("/api/v1/runway/tunnels/" + natan.tunnelId())
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(natan.name()))
        .andExpect(jsonPath("$.data.tunnelType").value("cloudflared"))
        .andExpect(jsonPath("$.data.catchAllRule").isNotEmpty())
        .andExpect(jsonPath("$.data.routes").isArray());
  }

  @Test
  @DisplayName("POST /api/v1/runway/tunnels/sync forces instant synchronization")
  void testSyncTunnelsEndpoint() throws Exception {
    mockMvc.perform(post("/api/v1/runway/tunnels/sync")
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  @DisplayName("POST /api/v1/runway/tunnels/{id}/sync synchronizes specific tunnel live state")
  void testSyncSpecificTunnelEndpoint() throws Exception {
    var accounts = accountRepository.findAll();
    if (!accounts.isEmpty()) {
      seedTestTunnelsIfMissing(accounts.get(0).getId());
    }
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());

    TunnelResponse kali = tunnels.stream()
        .filter(t -> "Kali".equalsIgnoreCase(t.name()))
        .findFirst()
        .orElse(tunnels.get(0));

    mockMvc.perform(post("/api/v1/runway/tunnels/" + kali.tunnelId() + "/sync")
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(kali.name()))
        .andExpect(jsonPath("$.data.tunnelType").value("cloudflared"));
  }

  @Test
  @DisplayName("Route management: addRoute and deleteRouteByIndex cleans DNS CNAME record from DB and Cloudflare")
  void testAddAndDeleteRouteEndpoints() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    TunnelResponse targetTunnel = tunnels.get(0);

    String initialCount = String.valueOf(targetTunnel.routesCount());

    // 1. Add route
    String tempRouteHostname = "temp-test-route-" + System.currentTimeMillis() + ".lola-cloud.dev";
    AddRouteRequest addReq = new AddRouteRequest(
        tempRouteHostname,
        "*",
        "http://localhost:3000",
        "http://localhost:3000");

    MvcResult addResult = mockMvc.perform(post("/api/v1/runway/tunnels/" + targetTunnel.id() + "/routes")
        .header("Authorization", "Bearer valid-admin-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(addReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.routes").isArray())
        .andReturn();

    JsonNode root = objectMapper.readTree(addResult.getResponse().getContentAsString());
    int countAfterAdd = root.path("data").path("routesCount").asInt();
    assertTrue(countAfterAdd > 0);

    // Verify CNAME record was added in DB
    assertTrue(recordRepository.findAll().stream()
        .anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName()) && "CNAME".equalsIgnoreCase(r.getType())),
        "DNS CNAME record should be created after route is added");

    // 2. Delete route by index
    int indexToDelete = countAfterAdd - 1;
    mockMvc.perform(delete("/api/v1/runway/tunnels/" + targetTunnel.id() + "/routes/" + indexToDelete)
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.routesCount").value(countAfterAdd - 1));

    // 3. Verify CNAME record was cleanly deleted from database
    boolean recordStillInDb = recordRepository.findAll().stream()
        .anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName()));
    assertFalse(recordStillInDb, "DNS CNAME record must not remain in the local database after route deletion");

    // 4. Verify CNAME record does not remain in Cloudflare
    var accounts = accountRepository.findAll();
    if (!accounts.isEmpty()) {
      var account = accounts.get(0);
      var zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
      for (var z : zones) {
        if (tempRouteHostname.endsWith(z.name())) {
          var records = cloudflareClient.listDnsRecords(account.getEmail(), account.getGlobalApiKey(), z.id(), z.name(),
              account.getId());
          boolean recordStillInCf = records.stream()
              .anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName()));
          assertFalse(recordStillInCf, "DNS CNAME record must not remain in Cloudflare after route deletion");
        }
      }
    }
  }

  @Test
  @DisplayName("Route management: addRoute and deleteRouteByHostname cleans DNS record")
  void testAddAndDeleteRouteByHostname() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    TunnelResponse targetTunnel = tunnels.get(0);

    String tempRouteHostname = "temp-test-route-byname-" + System.currentTimeMillis() + ".lola-cloud.dev";
    AddRouteRequest addReq = new AddRouteRequest(
        tempRouteHostname,
        "*",
        "http://localhost:3000",
        "http://localhost:3000");

    mockMvc.perform(post("/api/v1/runway/tunnels/" + targetTunnel.id() + "/routes")
        .header("Authorization", "Bearer valid-admin-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(addReq)))
        .andExpect(status().isOk());

    assertTrue(recordRepository.findAll().stream()
        .anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName())));

    mockMvc.perform(delete("/api/v1/runway/tunnels/" + targetTunnel.id() + "/routes")
        .header("Authorization", "Bearer valid-admin-token")
        .param("hostname", tempRouteHostname)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    assertFalse(recordRepository.findAll().stream()
        .anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName())),
        "DNS record must be deleted from local DB");

    var accounts = accountRepository.findAll();
    if (!accounts.isEmpty()) {
      var account = accounts.get(0);
      var zones = cloudflareClient.listZones(account.getEmail(), account.getGlobalApiKey());
      for (var z : zones) {
        if (tempRouteHostname.endsWith(z.name())) {
          var records = cloudflareClient.listDnsRecords(account.getEmail(), account.getGlobalApiKey(), z.id(), z.name(),
              account.getId());
          assertFalse(records.stream().anyMatch(r -> tempRouteHostname.equalsIgnoreCase(r.getName())),
              "DNS record must be deleted from Cloudflare");
        }
      }
    }
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id}/logs/stream requires authentication")
  void testStreamLogsUnauthorized() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());
    TunnelResponse tunnel = tunnels.get(0);

    mockMvc.perform(get("/api/v1/runway/tunnels/" + tunnel.id() + "/logs/stream"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id}/logs/stream rejects operator without runway::tunnel::read or runway::*")
  void testStreamLogsForbidden() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());
    TunnelResponse tunnel = tunnels.get(0);

    mockMvc.perform(get("/api/v1/runway/tunnels/" + tunnel.id() + "/logs/stream")
        .param("token", "valid-dns-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id}/logs/stream with ?token= query parameter initiates SSE stream")
  void testStreamLogsWithQueryToken() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());
    TunnelResponse tunnel = tunnels.get(0);

    mockMvc.perform(get("/api/v1/runway/tunnels/" + tunnel.id() + "/logs/stream")
        .param("token", "valid-admin-token")
        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id}/logs/stream with ?access_token= and runway::tunnel::read initiates SSE stream")
  void testStreamLogsWithAccessTokenParam() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());
    TunnelResponse tunnel = tunnels.get(0);

    mockMvc.perform(get("/api/v1/runway/tunnels/" + tunnel.id() + "/logs/stream")
        .param("access_token", "valid-tunnel-read-token")
        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  @Test
  @DisplayName("GET /api/v1/runway/tunnels/{id}/token returns full valid Cloudflare tunnel token")
  void testGetTunnelToken() throws Exception {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    assertFalse(tunnels.isEmpty());
    TunnelResponse tunnel = tunnels.get(0);

    MvcResult result = mockMvc.perform(get("/api/v1/runway/tunnels/" + tunnel.id() + "/token")
        .header("Authorization", "Bearer valid-admin-token"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    assertTrue(root.has("data"));
    JsonNode data = root.path("data");
    assertTrue(data.has("token"));
    String token = data.path("token").asText();
    assertNotNull(token);
    assertFalse(token.isBlank());
    assertTrue(token.startsWith("eyJh"));
  }

  @Test
  @DisplayName("Host server detection marks Kali tunnel and its connector as isHostServer=true")
  void testHostServerDetection() {
    List<TunnelResponse> tunnels = tunnelService.listTunnels();
    TunnelResponse kali = tunnels.stream()
        .filter(t -> "Kali".equalsIgnoreCase(t.name()))
        .findFirst()
        .orElse(null);

    assertNotNull(kali, "Kali tunnel must exist");
    assertTrue(Boolean.TRUE.equals(kali.isHostServer()), "Kali tunnel must have isHostServer=true");
    assertFalse(kali.connectors().isEmpty(), "Kali must have connectors");
    assertTrue(Boolean.TRUE.equals(kali.connectors().get(0).isHostServer()),
        "Kali connector must have isHostServer=true");

    TunnelResponse natan = tunnels.stream()
        .filter(t -> "natan".equalsIgnoreCase(t.name()))
        .findFirst()
        .orElse(null);
    if (natan != null) {
      assertFalse(Boolean.TRUE.equals(natan.isHostServer()), "natan tunnel must have isHostServer=false");
    }
  }

  @Test
  @DisplayName("POST /api/v1/runway/tunnels/expose-platform successfully provisions 4 ingress routes and DNS CNAMEs")
  void testExposePlatform() throws Exception {
    String payload = "{\"zoneName\":\"lola-cloud.dev\"}";

    MvcResult result = mockMvc.perform(post("/api/v1/runway/tunnels/expose-platform")
        .header("Authorization", "Bearer valid-admin-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tunnelId").isNotEmpty())
        .andExpect(jsonPath("$.data.zoneName").value("lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services").isArray())
        .andExpect(jsonPath("$.data.services.length()").value(6))
        .andExpect(jsonPath("$.data.services[0].subdomain").value("console-lola"))
        .andExpect(jsonPath("$.data.services[0].hostname").value("console-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[0].targetUrl").value("http://localhost:5173"))
        .andExpect(jsonPath("$.data.services[0].publicUrl").value("https://console-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[1].subdomain").value("lci-lola"))
        .andExpect(jsonPath("$.data.services[1].hostname").value("lci-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[1].targetUrl").value("http://localhost:8080"))
        .andExpect(jsonPath("$.data.services[1].publicUrl").value("https://lci-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[1].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[2].subdomain").value("runway-lola"))
        .andExpect(jsonPath("$.data.services[2].hostname").value("runway-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[2].targetUrl").value("http://localhost:8083"))
        .andExpect(jsonPath("$.data.services[2].publicUrl").value("https://runway-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[2].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[3].subdomain").value("relate-lola"))
        .andExpect(jsonPath("$.data.services[3].hostname").value("relate-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[3].targetUrl").value("http://localhost:8084"))
        .andExpect(jsonPath("$.data.services[3].publicUrl").value("https://relate-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[3].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[4].subdomain").value("store-lola"))
        .andExpect(jsonPath("$.data.services[4].hostname").value("store-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[4].targetUrl").value("http://localhost:8086"))
        .andExpect(jsonPath("$.data.services[4].publicUrl").value("https://store-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[4].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[5].subdomain").value("s3-lola"))
        .andExpect(jsonPath("$.data.services[5].hostname").value("s3-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[5].targetUrl").value("http://localhost:9000"))
        .andExpect(jsonPath("$.data.services[5].publicUrl").value("https://s3-lola.lola-cloud.dev"))
        .andExpect(jsonPath("$.data.services[5].status").value("ACTIVE"))
        .andReturn();

    // Verify GET /expose-platform/status
    mockMvc.perform(get("/api/v1/runway/tunnels/expose-platform/status")
        .header("Authorization", "Bearer valid-admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.services.length()").value(6))
        .andExpect(jsonPath("$.data.services[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[1].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[2].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[3].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[4].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.services[5].status").value("ACTIVE"));

    // Second call to expose-platform: Must be idempotent and never duplicate
    // records or routes
    mockMvc.perform(post("/api/v1/runway/tunnels/expose-platform")
        .header("Authorization", "Bearer valid-admin-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.services.length()").value(6));

    // Assert that exactly 1 DNS record exists for each platform hostname (zero
    // duplicates)
    List<String> expectedHostnames = List.of(
        "console-lola.lola-cloud.dev",
        "lci-lola.lola-cloud.dev",
        "runway-lola.lola-cloud.dev",
        "relate-lola.lola-cloud.dev",
        "store-lola.lola-cloud.dev",
        "s3-lola.lola-cloud.dev");
    for (String host : expectedHostnames) {
      long count = recordRepository.findAll().stream()
          .filter(r -> host.equalsIgnoreCase(r.getName()))
          .count();
      assertEquals(1, count, "Expected exactly 1 DNS record for " + host + " with zero duplicates");
    }
  }

  @Test
  @DisplayName("DELETE /api/v1/runway/tunnels/{id} deletes tunnel and cascades associated DNS CNAME records")
  void testDeleteTunnelCascades() throws Exception {
    TunnelResponse created = tunnelService
        .createTunnel(new com.miranda.luna.runway.infrastructure.web.dto.TunnelRequest(
            "del-test-gateway",
            null,
            "del-test.lola-cloud.dev",
            "http://localhost:4000"));
    assertNotNull(created);
    assertNotNull(created.id());

    // Verify CNAME was created in DB
    assertTrue(recordRepository.findAll().stream()
        .anyMatch(r -> "del-test.lola-cloud.dev".equalsIgnoreCase(r.getName())));

    // Delete tunnel via endpoint
    mockMvc.perform(delete("/api/v1/runway/tunnels/" + created.id())
        .header("Authorization", "Bearer valid-admin-token")
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.success").value(true));

    // Verify tunnel deleted from DB
    assertFalse(tunnelService.listTunnels().stream()
        .anyMatch(t -> created.id().equals(t.id())));

    // Verify CNAME deleted from DB
    assertFalse(recordRepository.findAll().stream()
        .anyMatch(r -> "del-test.lola-cloud.dev".equalsIgnoreCase(r.getName())));
  }

  @Test
  @DisplayName("createTunnel without explicit hostname/route creates tunnel with 0 routes and no CNAME record")
  void testCreateTunnelWithoutHostnameHasNoRoutes() {
    TunnelResponse created = tunnelService
        .createTunnel(new com.miranda.luna.runway.infrastructure.web.dto.TunnelRequest(
            "no-routes-tunnel",
            null,
            null,
            null));
    assertNotNull(created);
    assertEquals(0, created.routesCount());
    assertTrue(created.routes().isEmpty());
    assertTrue(created.ingressRoutes().isEmpty());
    assertFalse(recordRepository.findAll().stream()
        .anyMatch(r -> r.getName() != null && r.getName().contains("no-routes-tunnel")));

    try {
      tunnelService.deleteTunnel(created.id().toString());
    } catch (Exception e) {
      tunnelRepository.deleteById(created.id());
    }
  }

  @AfterEach
  void cleanUpCreatedTunnels() {
    tunnelRepository.findAll().stream()
        .filter(t -> !"Kali".equalsIgnoreCase(t.getName()) && !"natan".equalsIgnoreCase(t.getName()))
        .forEach(t -> {
          try {
            tunnelRepository.deleteById(t.getId());
          } catch (Exception ignored) {
          }
        });
  }

  @AfterAll
  void tearDownAll() {
    tunnelRepository.findAll().forEach(t -> {
      try {
        tunnelRepository.deleteById(t.getId());
      } catch (Exception ignored) {
      }
    });
    try {
      tunnelService.syncTunnels();
    } catch (Exception ignored) {
    }
  }
}
