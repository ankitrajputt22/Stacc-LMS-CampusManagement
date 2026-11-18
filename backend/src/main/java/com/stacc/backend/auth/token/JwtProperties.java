package com.stacc.backend.auth.token;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stacc.jwt")
public record JwtProperties(String secret) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("JWT_SECRET is not set");
        }
        if (decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT_SECRET is too short. It must be Base64 text for at least " + MIN_SECRET_BYTES + " random bytes");
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(decode(secret), "HmacSHA256");
    }


    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be Base64 text");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=(hidden)]";
    }
}
