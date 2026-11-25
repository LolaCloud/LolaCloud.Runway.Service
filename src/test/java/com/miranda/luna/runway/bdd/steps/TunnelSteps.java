package com.miranda.luna.runway.bdd.steps;

import com.miranda.luna.runway.bdd.CucumberSpringContextConfiguration;
import com.miranda.luna.runway.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class TunnelSteps {

    @Autowired
    private CucumberSpringContextConfiguration contextConfig;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private CommonSteps commonSteps;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.miranda.luna.runway.domain.repository.DnsRecordRepositoryPort recordRepository;

    private MockMvc getMockMvc() {
        return contextConfig.getMockMvc();
    }

    @Then("the response body should contain cloudflared daemon info")
    public void theResponseBodyShouldContainCloudflaredDaemonInfo() throws Exception {
        scenarioContext.getLatestResult()
                .andExpect(jsonPath("$.data.installed").isBoolean())
                .andExpect(jsonPath("$.data.running").isBoolean());
    }

    @Then("the response body should contain current WAN IP")
    public void theResponseBodyShouldContainCurrentWanIp() throws Exception {
        scenarioContext.getLatestResult()
                .andExpect(jsonPath("$.data.currentWanIp").isNotEmpty())
                .andExpect(jsonPath("$.data.syncStatus").isNotEmpty());
    }

    @Then("the auto IP fix response should indicate success")
    public void theAutoIpFixResponseShouldIndicateSuccess() throws Exception {
        scenarioContext.getLatestResult()
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.newIp").isNotEmpty());
    }

    @When("I create a tunnel with name {string}, hostname {string}, and service {string}")
    public void iCreateATunnel(String name, String hostname, String targetService) throws Exception {
        Map<String, String> payload = Map.of(
                "name", name,
                "hostname", hostname,
                "targetService", targetService
        );

        MockHttpServletRequestBuilder requestBuilder = post("/api/v1/runway/tunnels")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);

        MvcResult mvcResult = result.andReturn();
        if (mvcResult.getResponse().getStatus() == 201) {
            JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            if (dataNode.has("id")) {
                scenarioContext.set("createdTunnelId", dataNode.get("id").asText());
            }
        }
    }

    @Then("the response body should contain tunnel name {string}")
    public void theResponseBodyShouldContainTunnelName(String expectedName) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.name").value(expectedName));
    }

    @When("I add route with hostname {string} and service {string} to created tunnel")
    public void iAddRouteToCreatedTunnel(String hostname, String serviceUrl) throws Exception {
        String tunnelId = scenarioContext.get("createdTunnelId");
        Map<String, String> payload = Map.of(
                "hostname", hostname,
                "serviceUrl", serviceUrl
        );

        MockHttpServletRequestBuilder requestBuilder = post("/api/v1/runway/tunnels/" + tunnelId + "/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @Then("the tunnel routes count should be {int}")
    public void theTunnelRoutesCountShouldBe(int expectedCount) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.routesCount").value(expectedCount));
    }

    @When("I delete route {string} from created tunnel")
    public void iDeleteRouteFromCreatedTunnel(String hostname) throws Exception {
        String tunnelId = scenarioContext.get("createdTunnelId");
        MockHttpServletRequestBuilder requestBuilder = delete("/api/v1/runway/tunnels/" + tunnelId + "/routes")
                .param("hostname", hostname)
                .accept(MediaType.APPLICATION_JSON);

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @When("I delete the created tunnel")
    public void iDeleteTheCreatedTunnel() throws Exception {
        String tunnelId = scenarioContext.get("createdTunnelId");
        MockHttpServletRequestBuilder requestBuilder = delete("/api/v1/runway/tunnels/" + tunnelId)
                .accept(MediaType.APPLICATION_JSON);

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @io.cucumber.java.After
    public void cleanUpTestTunnelsAndRecords() {
        recordRepository.findAll().stream()
                .filter(r -> r.getName() != null && (r.getName().contains("lolacloud.internal") || r.getName().contains("ops")))
                .forEach(r -> {
                    try {
                        recordRepository.deleteById(r.getId());
                    } catch (Exception ignored) {
                    }
                });
    }
}
