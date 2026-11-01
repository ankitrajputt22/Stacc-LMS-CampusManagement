package com.stacc.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** The whole application starts with the prod profile and safe settings, and keeps development-only pages closed. */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "STACC_STORAGE_ENDPOINT=http://storage.internal:9000",
    "STACC_STORAGE_ACCESS_KEY=storage-user",
    "STACC_STORAGE_SECRET_KEY=storage-secret-value",
    "MYSQL_HOST=db.internal",
    "MYSQL_DATABASE=stacc",
    "MYSQL_USER=stacc",
    "MYSQL_PASSWORD=not-used-without-a-database"
})
class ProductionProfileTest extends DatabaseFreeApiTest {

    @Test
    void theApiDescriptionIsOffUnlessSwitchedOn() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void errorsStayInTheSharedSafeFormat() throws Exception {
        mockMvc.perform(get("/api/v1/lms/my-courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.requestId").exists());
    }
}
