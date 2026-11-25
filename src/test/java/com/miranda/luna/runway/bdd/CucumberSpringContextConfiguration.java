package com.miranda.luna.runway.bdd;

import com.miranda.luna.runway.infrastructure.security.LciAuthFilter;
import com.miranda.luna.runway.infrastructure.web.filter.RequestIdFilter;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@CucumberContextConfiguration
@SpringBootTest
public class CucumberSpringContextConfiguration {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @Autowired
    private LciAuthFilter lciAuthFilter;

    private MockMvc mockMvc;

    public MockMvc getMockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .addFilters(requestIdFilter, lciAuthFilter)
                    .build();
        }
        return mockMvc;
    }
}
