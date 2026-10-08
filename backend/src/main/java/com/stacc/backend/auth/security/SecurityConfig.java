package com.stacc.backend.auth.security;

import java.time.Duration;
import java.util.List;

import com.stacc.backend.auth.token.AccessTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The one shared Spring Security configuration for Stacc. Everything under /api/ needs a
 * signed-in account unless it is listed here as public.
 *
 * <p>Method security is on, so an operation can require a role with
 * {@code @PreAuthorize("hasRole('ADMIN')")} or {@code hasAnyRole('STUDENT', 'FACULTY')}.
 * Add such a rule only where a real feature needs it.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            StaccAuthenticationEntryPoint authenticationEntryPoint,
            StaccAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                // Lets the listed frontend addresses, and only those, call the API from a browser.
                // It decides who may ask. Every request still needs its token and its permissions.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(requests -> requests
                        // Public: the college login, because nobody has a token before signing in.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Public during development: the API documentation. It only describes the API.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Every other API route, including ones added later, needs a signed-in account.
                        .requestMatchers("/api/**").authenticated()
                        // Nothing else is served, so other paths simply return 404.
                        .anyRequest().permitAll())
                // Stacc will have its own sign-in, so Spring's built-in sign-in page,
                // sign-out handling, and browser password prompt are switched off.
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // A request that carries "Authorization: Bearer <access token>" is signed in for that
                // request only. A missing or unacceptable token on a protected route is answered with 401.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Not signed in is answered with 401. Signed in but not allowed is answered with 403.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // CSRF protection guards cookie-based sessions. The API is stateless and will
                // use tokens, so it is off. Review this if authentication ever moves to cookies.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * The browser rule for calls from another web address, used by the frontend dev server.
     * Only the origins in {@link CorsProperties} are accepted, and only for the API. Sign-in
     * uses a Bearer token in a header, so cookies and other browser credentials are not allowed.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
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
