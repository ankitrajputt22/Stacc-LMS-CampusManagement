package com.stacc.backend.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import com.stacc.backend.TestSecrets;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.permission.Permission;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.security.StaccUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

class AccessTokenServiceTest {

    // A made-up value that must never end up in a token.
    private static final String PASSWORD_HASH = "example-hash-value";

    private static final JwtProperties PROPERTIES = new JwtProperties(TestSecrets.JWT_SECRET, 15);
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private static AccessTokenService serviceAt(Instant time) {
        return new AccessTokenService(
                new JwtConfig().jwtEncoder(PROPERTIES), PROPERTIES, Clock.fixed(time, ZoneOffset.UTC));
    }

    private static JwtDecoder decoderFor(String secret) {
        return NimbusJwtDecoder.withSecretKey(new JwtProperties(secret, 15).signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static StaccUserPrincipal facultyPrincipal() {
        Role faculty = new Role(RoleName.FACULTY);
        faculty.assignPermission(new Permission("COURSE_VIEW"));
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);
        ReflectionTestUtils.setField(account, "id", 12L);
        account.assignRole(faculty);
        return StaccUserPrincipal.from(account);
    }

    @Test
    void tokenIsSignedAndVerifiesWithTheConfiguredKey() {
        AccessToken token = serviceAt(NOW).issue(facultyPrincipal());

        assertFalse(token.value().isBlank());
        Jwt jwt = decoderFor(TestSecrets.JWT_SECRET).decode(token.value());
        assertEquals("HS256", jwt.getHeaders().get("alg"));
    }

    @Test
    void tokenSignedWithOneKeyDoesNotVerifyWithAnother() {
        AccessToken token = serviceAt(NOW).issue(facultyPrincipal());

        assertThrows(JwtException.class, () -> decoderFor(TestSecrets.OTHER_JWT_SECRET).decode(token.value()));
    }

    @Test
    void tokenIdentifiesTheAccountAndItsAuthorities() {
        Jwt jwt = decoderFor(TestSecrets.JWT_SECRET).decode(serviceAt(NOW).issue(facultyPrincipal()).value());

        assertEquals("stacc", jwt.getClaimAsString("iss"));
        assertEquals("EMP1024", jwt.getSubject());
        assertEquals(12L, jwt.<Long>getClaim("accountId"));
        assertEquals(List.of("COURSE_VIEW", "ROLE_FACULTY"), jwt.getClaimAsStringList("authorities"));
        assertNull(jwt.getId());
    }

    @Test
    void tokenExpiresFifteenMinutesAfterItIsIssued() {
        AccessToken token = serviceAt(NOW).issue(facultyPrincipal());
        Jwt jwt = decoderFor(TestSecrets.JWT_SECRET).decode(token.value());

        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertEquals(NOW, jwt.getIssuedAt());
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertEquals(900, token.expiresInSeconds());
    }

    @Test
    void oldTokenIsNoLongerAccepted() {
        AccessToken issuedAnHourAgo = serviceAt(NOW.minus(1, ChronoUnit.HOURS)).issue(facultyPrincipal());

        assertThrows(JwtException.class, () -> decoderFor(TestSecrets.JWT_SECRET).decode(issuedAnHourAgo.value()));
    }

    @Test
    void tokenCarriesOnlyTheExpectedClaimsAndNoPasswordData() {
        AccessToken token = serviceAt(NOW).issue(facultyPrincipal());
        Jwt jwt = decoderFor(TestSecrets.JWT_SECRET).decode(token.value());
        String payload = new String(
                Base64.getUrlDecoder().decode(token.value().split("\\.")[1]), StandardCharsets.UTF_8);

        assertEquals(Set.of("iss", "sub", "iat", "exp", "accountId", "authorities"), jwt.getClaims().keySet());
        assertFalse(payload.toLowerCase().contains("password"));
        assertFalse(payload.contains(PASSWORD_HASH));
        assertFalse(token.toString().contains(token.value()));
    }
}
