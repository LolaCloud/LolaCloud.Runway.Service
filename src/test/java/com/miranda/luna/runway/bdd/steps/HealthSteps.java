package com.miranda.luna.runway.bdd.steps;

import com.miranda.luna.runway.bdd.context.ScenarioContext;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class HealthSteps {

    @Autowired
    private ScenarioContext scenarioContext;

    @Then("the response body should contain data status {string}")
    public void theResponseBodyShouldContainDataStatus(String expectedStatus) throws Exception {
        scenarioContext.getLatestResult().andExpect(jsonPath("$.data.status").value(expectedStatus));
    }
}
