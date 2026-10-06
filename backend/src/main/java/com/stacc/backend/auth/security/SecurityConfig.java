package com.stacc.backend.auth.security;

import com.stacc.backend.auth.token.AccessTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The one shared Spring Security configuration for Stacc. It only sets up the
 * security foundation: sign-in and access rules are added in later phases.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, StaccAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
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
                // A request that carries "Authorization: Bearer <access token>" is signed in for that
                // request only. A token that cannot be accepted is answered with 401, never ignored.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                // CSRF protection guards cookie-based sessions. The API is stateless and will
                // use tokens, so it is off. Review this if authentication ever moves to cookies.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // The token's "authorities" claim already holds the final names, such as ROLE_STUDENT or a
    // permission code, so they are used as they are. Spring's default "SCOPE_" prefix is not added.
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AccessTokenService.AUTHORITIES_CLAIM);
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * The one password encoder for Stacc. New passwords are hashed with BCrypt, and each
     * hash records its algorithm so it can be upgraded later. Always use this bean:
     * encode(...) to create a hash and matches(...) to check a password.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Checks a login ID and password against the stored accounts, using Spring Security's
     * standard provider with the shared password encoder. Nothing calls it over HTTP yet;
     * the sign-in endpoint will use it in a later phase.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Check the password first and the account status second. A disabled account then
        // fails exactly like any other wrong login unless the caller knows its password.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }
}
