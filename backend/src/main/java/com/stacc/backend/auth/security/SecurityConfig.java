package com.stacc.backend.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The one shared Spring Security configuration for Stacc. It only sets up the
 * security foundation: sign-in and access rules are added in later phases.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Temporary: Stacc has no sign-in yet, so nothing can be protected.
                // Real access rules replace this when authentication is built.
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Stacc will have its own sign-in, so Spring's built-in sign-in page,
                // sign-out handling, and browser password prompt are switched off.
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protection guards cookie-based sessions. The API is stateless and will
                // use tokens, so it is off. Review this if authentication ever moves to cookies.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
