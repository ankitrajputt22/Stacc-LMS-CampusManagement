package com.stacc.backend.auth.token;

import java.time.Clock;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** Signs access tokens with HMAC SHA-256 using the configured secret. */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.signingKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Checks incoming access tokens with the same secret and algorithm that signed them.
     * A token is accepted only if its signature is valid, it has not expired, it was issued
     * by Stacc, and it names an account.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(AccessTokenService.ISSUER),
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<Object>(AccessTokenService.ACCOUNT_ID_CLAIM, Objects::nonNull)));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
