package com.stacc.backend.auth.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The one shared Spring Security configuration for Stacc. Everything under /api/v1/ needs a
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
            CorsConfigurationSource corsConfigurationSource,
            SessionService sessionService,
            JwtDecoder jwtDecoder)
            throws Exception {
        SessionCheckingJwtConverter sessionCheck = new SessionCheckingJwtConverter(sessionService);
        JwtAuthenticationProvider cookieTokens = new JwtAuthenticationProvider(jwtDecoder);
        cookieTokens.setJwtAuthenticationConverter(sessionCheck);
        http
                // Lets the listed frontend addresses, and only those, call the API from a browser.
                // It decides who may ask. Every request still needs its token and its permissions.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(requests -> requests
                        // Public: the college login, because nobody has a token before signing in.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // Public during development: the API documentation. It only describes the API.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // The admin workspace is for admin accounts only, checked before a request body is even
                        // read. Each route still needs its own permission as well.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
                // An API client signs in with an "Authorization: Bearer" header; a browser with the HttpOnly session
                // cookie, read by SessionCookieAuthenticationFilter after the CSRF check. Either way the token's session
                // must still be open and its account active. A missing or unacceptable token gets 401.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new SessionTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(sessionCheck))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Not signed in is answered with 401. Signed in but not allowed is answered with 403.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // A cookie is sent by the browser on its own, so a request signed in by the session cookie must also
                // prove it came from Stacc's own page: the XSRF-TOKEN cookie, which only Stacc's pages can read, copied
                // into the X-XSRF-TOKEN header (Axios does this). Requests with a Bearer header, requests without the
                // cookie, sign-in itself, and safe methods have nothing to forge and are not checked.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(xsrfCookies())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(SecurityConfig::needsCsrfCheck)
                        // Spring would replace the XSRF token whenever it sees a signed-in request it has not stored,
                        // which without servlet sessions is every request, so the browser would never keep one. A new
                        // sign-in already gets a new session of its own; the XSRF token simply stays.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new SessionCookieAuthenticationFilter(new ProviderManager(cookieTokens),
                        authenticationEntryPoint), CsrfCookieFilter.class);
        return http.build();
    }

    /** The XSRF-TOKEN cookie, readable by Stacc's own pages only (same origin), for the whole site. */
    private static CookieCsrfTokenRepository xsrfCookies() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        return repository;
    }

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    static boolean needsCsrfCheck(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod()) && SessionTokenResolver.usesSessionCookie(request)
                && !SessionTokenResolver.isLogin(request);
    }

    /**
     * The browser rule for calls from another web address, used by the frontend dev server.
     * Only the origins in {@link CorsProperties} are accepted, and only for the API. They may send the
     * session cookie, so the list is exact origins only; a wildcard is refused when the backend starts.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN",
                RequestIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
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
