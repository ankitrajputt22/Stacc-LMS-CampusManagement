package com.stacc.backend.auth.token;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access-token settings. The signing secret comes from the JWT_SECRET environment
 * variable and has no default, so the application refuses to start without a strong one.
 */
@ConfigurationProperties(prefix = "stacc.jwt")
public record JwtProperties(String secret, int accessTokenMinutes) {

    static final int MIN_SECRET_BYTES = 32;
    static final int MAX_ACCESS_TOKEN_MINUTES = 60;

    public JwtProperties {
        // An environment variable that is not set arrives here as the unresolved text "${JWT_SECRET}".
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("JWT_SECRET is not set");
        }
        if (decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT_SECRET is too short. It must be Base64 text for at least " + MIN_SECRET_BYTES + " random bytes");
        }
        if (accessTokenMinutes < 1 || accessTokenMinutes > MAX_ACCESS_TOKEN_MINUTES) {
            throw new IllegalArgumentException(
                    "JWT_ACCESS_TOKEN_MINUTES must be between 1 and " + MAX_ACCESS_TOKEN_MINUTES);
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(decode(secret), "HmacSHA256");
    }

    public Duration accessTokenLifetime() {
        return Duration.ofMinutes(accessTokenMinutes);
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be Base64 text");
        }
    }

    // A record's default text form would include the secret, so it is left out here.
    @Override
    public String toString() {
        return "JwtProperties[accessTokenMinutes=" + accessTokenMinutes + "]";
    }
}
