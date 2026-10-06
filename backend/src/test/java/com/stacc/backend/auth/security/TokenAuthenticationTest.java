package com.stacc.backend.auth.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sends requests with good and bad access tokens. "/api/test/whoami" exists only in test code
 * and reports who Spring Security thinks is calling.
 */
class TokenAuthenticationTest extends DatabaseFreeApiTest {

    private static final String WHO_AM_I = "/api/test/whoami";

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static ResultActions expectRefused(ResultActions result, String path) throws Exception {
        return result
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired access token."))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(jsonPath("$.authenticated").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("Jwt"))))
                .andExpect(content().string(not(containsString("signature"))));
    }

    @Test
    void validTokenSignsTheRequestInAsItsAccount() throws Exception {
        getWithToken(WHO_AM_I, TestTokens.valid("2408400100011", 12, "ROLE_STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("2408400100011"))
                .andExpect(jsonPath("$.accountId").value(12));
    }

    @Test
    void rolesAndPermissionsKeepTheirExactNames() throws Exception {
        getWithToken(WHO_AM_I, TestTokens.valid("EMP1024", 7, "ATTENDANCE_MARK", "COURSE_VIEW", "ROLE_ADMIN", "ROLE_FACULTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities", hasItems("ATTENDANCE_MARK", "COURSE_VIEW", "ROLE_ADMIN", "ROLE_FACULTY")))
                .andExpect(jsonPath("$.authorities", everyItem(not(startsWith("SCOPE_")))))
                .andExpect(jsonPath("$.authorities", everyItem(not(startsWith("ROLE_ROLE_")))));
    }

    @Test
    void tokenIssuedByTheApplicationIsAccepted() throws Exception {
        UserAccount account = new UserAccount("EMP1024", "example-hash-value");
        ReflectionTestUtils.setField(account, "id", 7L);
        account.assignRole(new Role(RoleName.FACULTY));
        String token = accessTokenService.issue(StaccUserPrincipal.from(account)).value();

        getWithToken(WHO_AM_I, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EMP1024"))
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.authorities", hasItems("ROLE_FACULTY")));
    }

    @Test
    void tokenFromARealLoginWorksOnTheNextRequest() throws Exception {
        UserAccount account = new UserAccount("2408400100011", passwordEncoder.encode("TestPassword123!"));
        ReflectionTestUtils.setField(account, "id", 12L);
        account.assignRole(new Role(RoleName.STUDENT));
        when(userAccountRepository.findWithRolesAndPermissionsByLoginId(anyString())).thenReturn(Optional.of(account));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"2408400100011\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = login.getResponse().getContentAsString()
                .replaceAll(".*\"accessToken\":\"([^\"]*)\".*", "$1");

        getWithToken(WHO_AM_I, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2408400100011"))
                .andExpect(jsonPath("$.authorities", hasItems("ROLE_STUDENT")));
    }

    @Test
    void tokenRequestsNeedNoDatabaseLookupAndNoSession() throws Exception {
        MvcResult result = getWithToken(WHO_AM_I, TestTokens.valid("2408400100011", 12, "ROLE_STUDENT"))
                .andExpect(status().isOk())
                .andReturn();

        verifyNoInteractions(userAccountRepository);
        assertNull(result.getRequest().getSession(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-token", "aaa.bbb.ccc", "eyJhbGciOiJIUzI1NiJ9.e30."})
    void malformedTokenIsRefused(String token) throws Exception {
        expectRefused(getWithToken(WHO_AM_I, token), WHO_AM_I);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRefused() throws Exception {
        String forged = TestTokens.signed(
                TestSecrets.OTHER_JWT_SECRET, MacAlgorithm.HS256, TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"));

        expectRefused(getWithToken(WHO_AM_I, forged), WHO_AM_I);
    }

    @Test
    void tokenWhoseContentsWereChangedIsRefused() throws Exception {
        String token = TestTokens.valid("2408400100011", 12, "ROLE_STUDENT");

        expectRefused(getWithToken(WHO_AM_I, TestTokens.tampered(token)), WHO_AM_I);
    }

    @Test
    void expiredTokenIsRefused() throws Exception {
        Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        AccessTokenService pastService = new AccessTokenService(
                jwtEncoder, new JwtProperties(TestSecrets.JWT_SECRET, 15), Clock.fixed(anHourAgo, ZoneOffset.UTC));
        UserAccount account = new UserAccount("EMP1024", "example-hash-value");
        ReflectionTestUtils.setField(account, "id", 7L);
        account.assignRole(new Role(RoleName.FACULTY));

        expectRefused(getWithToken(WHO_AM_I, pastService.issue(StaccUserPrincipal.from(account)).value()), WHO_AM_I);
    }

    @Test
    void tokenFromAnotherIssuerIsRefused() throws Exception {
        String token = TestTokens.withClaims(
                TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"), claims -> claims.issuer("someone-else"));

        expectRefused(getWithToken(WHO_AM_I, token), WHO_AM_I);
    }

    @Test
    void tokenWithoutAnAccountOrAnExpiryIsRefused() throws Exception {
        String noAccount = TestTokens.withClaims(
                TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"),
                claims -> claims.claims(all -> all.remove("accountId")));
        String noExpiry = TestTokens.withClaims(
                TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"),
                claims -> claims.claims(all -> all.remove("exp")));

        expectRefused(getWithToken(WHO_AM_I, noAccount), WHO_AM_I);
        expectRefused(getWithToken(WHO_AM_I, noExpiry), WHO_AM_I);
    }

    @Test
    void unsignedTokenAndOtherAlgorithmsAreRefused() throws Exception {
        String otherAlgorithm = TestTokens.signed(
                TestSecrets.JWT_SECRET, MacAlgorithm.HS384, TestTokens.validClaims("EMP1024", 7, "ROLE_ADMIN"));

        expectRefused(getWithToken(WHO_AM_I, TestTokens.unsigned("EMP1024", 7)), WHO_AM_I);
        expectRefused(getWithToken(WHO_AM_I, otherAlgorithm), WHO_AM_I);
    }

    @Test
    void tokenInTheUrlIsNotAccepted() throws Exception {
        String token = TestTokens.valid("2408400100011", 12, "ROLE_STUDENT");

        // A token in the URL is ignored, so the request counts as not signed in.
        mockMvc.perform(get(WHO_AM_I).param("access_token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
        mockMvc.perform(get(WHO_AM_I).param("token", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void unknownApiRouteIsNotFoundWithAValidTokenButRefusedWithABadOne() throws Exception {
        getWithToken("/api/does-not-exist", TestTokens.valid("2408400100011", 12, "ROLE_STUDENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        expectRefused(getWithToken("/api/does-not-exist", "not-a-token"), "/api/does-not-exist");
    }

    @Test
    void apiDocumentationDescribesBearerTokensButDoesNotRequireThem() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }
}
