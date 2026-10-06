package com.stacc.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class StaccApplicationTests extends DatabaseFreeApiTest {

    @Test
    void apiDocumentationIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Stacc API"));
    }

    @Test
    void unknownUrlReturnsNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/does-not-exist"));

        // Inside /api the caller must be signed in before the route is even looked up.
        String token = TestTokens.valid("2408400100011", 12, "ROLE_STUDENT");
        mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void unsupportedHttpMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/v3/api-docs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value("The request method is not supported for this resource."))
                .andExpect(jsonPath("$.path").value("/v3/api-docs"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }
}
