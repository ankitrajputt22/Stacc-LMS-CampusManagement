package com.stacc.backend.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import com.stacc.backend.TestSecrets;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Builds access tokens for tests, including deliberately broken ones. */
public final class TestTokens {

    private TestTokens() {
    }

    /** Claims of a normal, currently valid Stacc access token. */
    public static JwtClaimsSet.Builder validClaims(String loginId, long accountId, String... authorities) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return JwtClaimsSet.builder()
                .issuer(AccessTokenService.ISSUER)
                .subject(loginId)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .claim(AccessTokenService.ACCOUNT_ID_CLAIM, accountId)
                .claim(AccessTokenService.AUTHORITIES_CLAIM, List.of(authorities));
    }

    /** A token signed with the test secret, exactly as the application signs them. */
    public static String valid(String loginId, long accountId, String... authorities) {
        return signed(TestSecrets.JWT_SECRET, MacAlgorithm.HS256, validClaims(loginId, accountId, authorities));
    }

    /** A correctly signed token that stopped being valid 45 minutes ago. */
    public static String expired(String loginId, long accountId, String... authorities) {
        Instant anHourAgo = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.HOURS);
        return withClaims(
                validClaims(loginId, accountId, authorities),
                claims -> claims.issuedAt(anHourAgo).expiresAt(anHourAgo.plus(15, ChronoUnit.MINUTES)));
    }

    /** A token signed with the test secret after the claims have been changed. */
    public static String withClaims(JwtClaimsSet.Builder claims, Consumer<JwtClaimsSet.Builder> change) {
        change.accept(claims);
        return signed(TestSecrets.JWT_SECRET, MacAlgorithm.HS256, claims);
    }

    public static String signed(String base64Secret, MacAlgorithm algorithm, JwtClaimsSet.Builder claims) {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "Hmac" + algorithm.getName().replace("HS", "SHA"));
        return NimbusJwtEncoder.withSecretKey(key).algorithm(algorithm).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims.build()))
                .getTokenValue();
    }

    /** A token with no signature at all, claiming the "none" algorithm. */
    public static String unsigned(String loginId, long accountId) {
        Instant now = Instant.now();
        String header = "{\"alg\":\"none\"}";
        String payload = "{\"iss\":\"stacc\",\"sub\":\"" + loginId + "\",\"accountId\":" + accountId
                + ",\"authorities\":[\"ROLE_ADMIN\"],\"iat\":" + now.getEpochSecond()
                + ",\"exp\":" + now.plus(15, ChronoUnit.MINUTES).getEpochSecond() + "}";
        return encode(header) + "." + encode(payload) + ".";
    }

    /** The same token with its payload swapped for one that claims more rights. */
    public static String tampered(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("ROLE_STUDENT", "ROLE_ADMIN");
        return parts[0] + "." + encode(payload) + "." + parts[2];
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
