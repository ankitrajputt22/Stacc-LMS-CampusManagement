package com.stacc.backend.auth.token;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
