package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

class SecurityConfigTest extends DatabaseFreeApiTest {

    @Test
    void apiDocumentationIsPublic() throws Exception {
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
        mockMvc.perform(post("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void requestsDoNotCreateAServerSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void accountsComeOnlyFromStaccNotFromAGeneratedUser() {
        assertEquals(1, context.getBeanNamesForType(UserDetailsService.class).length);
        assertInstanceOf(StaccUserDetailsService.class, context.getBean(UserDetailsService.class));
    }

    @Test
    void thereIsOneAuthenticationManagerForTheFutureSignIn() {
        assertEquals(1, context.getBeanNamesForType(AuthenticationManager.class).length);
    }

    @Test
    void thereIsOneSharedPasswordEncoder() {
        assertEquals(1, context.getBeanNamesForType(PasswordEncoder.class).length);
    }
}
