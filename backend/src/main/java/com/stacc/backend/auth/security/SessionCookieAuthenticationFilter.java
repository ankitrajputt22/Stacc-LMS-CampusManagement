package com.stacc.backend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs a browser request in by its HttpOnly session cookie. It runs after the CSRF check, so a change signed in by
 * the cookie has already proved it came from Stacc's own page. The token is checked exactly like a Bearer token: the
 * same signature, expiry, and open-session rules. A request with an Authorization header, and sign-in itself, are left
 * to the Bearer handling.
 */
class SessionCookieAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager tokens;
    private final AuthenticationEntryPoint entryPoint;

    SessionCookieAuthenticationFilter(AuthenticationManager tokens, AuthenticationEntryPoint entryPoint) {
        this.tokens = tokens;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SessionTokenResolver.usesSessionCookie(request) || SessionTokenResolver.isLogin(request)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication signedIn;
        try {
            signedIn = tokens.authenticate(new BearerTokenAuthenticationToken(SessionCookies.read(request)));
        } catch (AuthenticationException refused) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, refused);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(signedIn);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
