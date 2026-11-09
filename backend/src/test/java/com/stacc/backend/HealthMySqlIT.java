package com.stacc.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The health checks with a real database: readiness, which Docker waits for, follows the database. */
class HealthMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void readinessIsUpWhileTheDatabaseAnswers() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        // The test's file store does not exist, so the overall health says so, but readiness above is unaffected.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.storage.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
    }
}
