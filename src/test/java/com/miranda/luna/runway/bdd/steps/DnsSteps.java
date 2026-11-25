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

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class DnsSteps {

    @Autowired
    private CucumberSpringContextConfiguration contextConfig;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private CommonSteps commonSteps;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc getMockMvc() {
        return contextConfig.getMockMvc();
    }

    @When("I create a DNS record with zoneId {string}, name {string}, type {string}, content {string}, ttl {int}, and autoSync {boolean}")
    public void iCreateADnsRecord(String zoneId, String name, String type, String content, int ttl, boolean autoSync) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("zoneId", zoneId);
        payload.put("name", name);
        payload.put("type", type);
        payload.put("content", content);
        payload.put("ttl", ttl);
        payload.put("proxied", false);
        payload.put("autoSync", autoSync);

        MockHttpServletRequestBuilder requestBuilder = post("/api/v1/runway/records")
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
                scenarioContext.set("createdDnsRecordId", dataNode.get("id").asText());
            }
        }
    }

    @Then("the created record should have name {string}")
    public void theCreatedRecordShouldHaveName(String expectedName) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.name").value(expectedName));
    }

    @Then("the created record should have content {string}")
    public void theCreatedRecordShouldHaveContent(String expectedContent) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.content").value(expectedContent));
    }

    @When("I get the DNS record by created ID")
    public void iGetTheDnsRecordByCreatedId() throws Exception {
        String id = scenarioContext.get("createdDnsRecordId");
        MockHttpServletRequestBuilder requestBuilder = get("/api/v1/runway/records/" + id)
                .accept(MediaType.APPLICATION_JSON);

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @Then("the response body should contain record content {string}")
    public void theResponseBodyShouldContainRecordContent(String expectedContent) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.content").value(expectedContent));
    }

    @When("I update the DNS record with new content {string} and proxied {boolean}")
    public void iUpdateTheDnsRecord(String newContent, boolean proxied) throws Exception {
        String id = scenarioContext.get("createdDnsRecordId");
        Map<String, Object> payload = Map.of(
                "content", newContent,
                "proxied", proxied
        );

        MockHttpServletRequestBuilder requestBuilder = put("/api/v1/runway/records/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @When("I toggle auto-sync to {boolean} on the DNS record")
    public void iToggleAutoSync(boolean autoSync) throws Exception {
        String id = scenarioContext.get("createdDnsRecordId");
        Map<String, Object> payload = Map.of("autoSync", autoSync);

        MockHttpServletRequestBuilder requestBuilder = patch("/api/v1/runway/records/" + id + "/auto-sync")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @Then("the response body auto-sync should be {boolean}")
    public void theResponseBodyAutoSyncShouldBe(boolean expectedAutoSync) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.autoSync").value(expectedAutoSync));
    }

    @When("I delete the created DNS record")
    public void iDeleteTheCreatedDnsRecord() throws Exception {
        String id = scenarioContext.get("createdDnsRecordId");
        MockHttpServletRequestBuilder requestBuilder = delete("/api/v1/runway/records/" + id)
                .accept(MediaType.APPLICATION_JSON);

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }
}
