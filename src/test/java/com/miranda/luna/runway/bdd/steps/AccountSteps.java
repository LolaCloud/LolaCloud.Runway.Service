package com.miranda.luna.runway.bdd.steps;

import com.miranda.luna.runway.bdd.CucumberSpringContextConfiguration;
import com.miranda.luna.runway.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class AccountSteps {

    @Autowired
    private CucumberSpringContextConfiguration contextConfig;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private CommonSteps commonSteps;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.miranda.luna.runway.domain.repository.CloudflareAccountRepositoryPort accountRepository;

    @Autowired
    private com.miranda.luna.runway.application.service.AccountService accountService;

    private MockMvc getMockMvc() {
        return contextConfig.getMockMvc();
    }

    @When("I validate credentials with email {string} and apiKey {string}")
    public void iValidateCredentialsWithEmailAndApiKey(String email, String apiKey) throws Exception {
        Map<String, String> payload = Map.of(
                "email", email,
                "apiKey", apiKey
        );

        MockHttpServletRequestBuilder requestBuilder = post("/api/v1/runway/accounts/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @Then("the validation result should be valid")
    public void theValidationResultShouldBeValid() throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.valid").value(true));
    }

    @Then("the validation result should be invalid")
    public void theValidationResultShouldBeInvalid() throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.valid").value(false));
    }

    @When("I connect a Cloudflare account with name {string}, email {string}, and apiKey {string}")
    public void iConnectACloudflareAccountWithNameEmailAndApiKey(String name, String email, String apiKey) throws Exception {
        accountRepository.findAll().stream()
                .filter(a -> a.getEmail().equalsIgnoreCase(email.trim()))
                .forEach(a -> accountRepository.deleteById(a.getId()));

        Map<String, String> payload = Map.of(
                "name", name,
                "email", email,
                "apiKey", apiKey
        );

        MockHttpServletRequestBuilder requestBuilder = post("/api/v1/runway/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));

        commonSteps.getHeaders().forEach(requestBuilder::header);
        ResultActions result = getMockMvc().perform(requestBuilder);
        scenarioContext.setLatestResult(result);
    }

    @Then("the response body should contain account name {string}")
    public void theResponseBodyShouldContainAccountName(String expectedName) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.name").value(expectedName));
    }

    @Then("the response body should contain maskedApiKey")
    public void theResponseBodyShouldContainMaskedApiKey() throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.maskedApiKey").isNotEmpty());
    }

    @Then("the accounts list should contain email {string}")
    public void theAccountsListShouldContainEmail(String expectedEmail) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data[*].email", hasItem(expectedEmail)));
    }

    @io.cucumber.java.After
    public void cleanUpTestAccounts() {
        accountRepository.findAll().stream()
                .filter(a -> a.getName().contains("Test") || a.getEmail().contains("lolacloud.internal") || a.getEmail().contains("example.com"))
                .forEach(a -> {
                    try {
                        accountService.deleteAccount(a.getId());
                    } catch (Exception ignored) {
                    }
                });
    }
}
