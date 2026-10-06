package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Checks the security foundation without a database. Every request goes through
 * the real Spring Security filter chain.
 */
@SpringBootTest(properties =
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void currentRoutesAreOpenWhileSignInDoesNotExist() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void springGeneratedSignInPageIsNotUsed() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
        mockMvc.perform(post("/logout"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void browserPasswordPromptIsNotUsed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        // Made-up Basic credentials are simply ignored, because HTTP Basic is switched off.
        mockMvc.perform(get("/v3/api-docs").with(request -> {
                    request.addHeader(HttpHeaders.AUTHORIZATION, "Basic c29tZW9uZTpUZXN0UGFzc3dvcmQxMjMh");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void requestsThatChangeDataAreNotBlockedForAMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void requestsDoNotCreateAServerSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void springBootDoesNotCreateAGeneratedUser() {
        assertEquals(0, context.getBeanNamesForType(UserDetailsService.class).length);
    }
}
