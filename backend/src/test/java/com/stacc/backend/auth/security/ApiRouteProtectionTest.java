package com.stacc.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.TestSecrets;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.auth.token.JwtProperties;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks which routes need a signed-in account. "/api/test/whoami" exists only in test code
 * and stands in for any protected API route.
 */
class ApiRouteProtectionTest extends DatabaseFreeApiTest {

    private static final String PROTECTED_ROUTE = "/api/test/whoami";

    @Autowired
    private JwtEncoder jwtEncoder;

    private static String studentToken() {
        return TestTokens.valid("2408400100011", 12, "ROLE_STUDENT");
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static ResultActions expectUnauthorized(ResultActions result, String path, String message)
            throws Exception {
        return result
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void protectedRouteWithoutATokenAsksForAuthentication() throws Exception {
        expectUnauthorized(mockMvc.perform(get(PROTECTED_ROUTE)), PROTECTED_ROUTE, "Authentication is required.")
                .andExpect(jsonPath("$.authenticated").doesNotExist());
    }

    @Test
    void protectedRouteWithAValidTokenIsReachedAsThatAccount() throws Exception {
        getWithToken(PROTECTED_ROUTE, TestTokens.valid("EMP1024", 7, "COURSE_VIEW", "ROLE_ADMIN", "ROLE_FACULTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("EMP1024"))
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.authorities", hasItems("COURSE_VIEW", "ROLE_ADMIN", "ROLE_FACULTY")));
    }

    @Test
    void anySignedInAccountMayUseAProtectedRouteForNow() throws Exception {
        getWithToken(PROTECTED_ROUTE, studentToken()).andExpect(status().isOk());
        getWithToken(PROTECTED_ROUTE, TestTokens.valid("EMP3000", 30)).andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithABadTokenIsRefused() throws Exception {
        String wrongKey = TestTokens.signed(
                TestSecrets.OTHER_JWT_SECRET, MacAlgorithm.HS256, TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"));

        expectUnauthorized(getWithToken(PROTECTED_ROUTE, "not-a-token"), PROTECTED_ROUTE, "Invalid or expired access token.");
        expectUnauthorized(getWithToken(PROTECTED_ROUTE, wrongKey), PROTECTED_ROUTE, "Invalid or expired access token.");
    }

    @Test
    void protectedRouteWithAnExpiredTokenIsRefused() throws Exception {
        Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        AccessTokenService pastService = new AccessTokenService(
                jwtEncoder, new JwtProperties(TestSecrets.JWT_SECRET, 15), Clock.fixed(anHourAgo, ZoneOffset.UTC));
        UserAccount account = new UserAccount("EMP1024", "example-hash-value");
        ReflectionTestUtils.setField(account, "id", 7L);
        account.assignRole(new Role(RoleName.FACULTY));
        String expired = pastService.issue(StaccUserPrincipal.from(account)).value();

        expectUnauthorized(getWithToken(PROTECTED_ROUTE, expired), PROTECTED_ROUTE, "Invalid or expired access token.");
    }

    @Test
    void loginIsReachableWithoutAToken() throws Exception {
        when(userAccountRepository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.empty());

        // A malformed request and wrong credentials are answered by the login itself, not by the token check.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"EMP9999\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login ID or password."));
    }

    @Test
    void onlyThePostLoginIsPublicInsideTheAuthPaths() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/auth/login")), "/api/auth/login", "Authentication is required.");
        expectUnauthorized(mockMvc.perform(delete("/api/auth/login")), "/api/auth/login", "Authentication is required.");
        expectUnauthorized(mockMvc.perform(post("/api/auth/anything-else")), "/api/auth/anything-else", "Authentication is required.");
    }

    @Test
    void apiDocumentationStaysPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void unknownApiRouteNeedsATokenFirstAndIsThenNotFound() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/does-not-exist")), "/api/does-not-exist", "Authentication is required.");
        expectUnauthorized(mockMvc.perform(post("/api/does-not-exist")), "/api/does-not-exist", "Authentication is required.");

        getWithToken("/api/does-not-exist", studentToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    @Test
    void routesOutsideTheApiKeepTheirNormalBehaviour() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/login")).andExpect(status().isNotFound());
        mockMvc.perform(get("/apidocs")).andExpect(status().isNotFound());
    }
}
