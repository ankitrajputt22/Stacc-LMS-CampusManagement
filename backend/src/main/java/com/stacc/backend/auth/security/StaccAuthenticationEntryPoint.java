package com.stacc.backend.auth.security;

import java.io.IOException;

import com.stacc.backend.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers a request that is not signed in, or whose access token cannot be accepted, with
 * HTTP 401 in the common error format. The reason a token was refused is never sent to the client.
 */
@Component
public class StaccAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
    static final String INVALID_TOKEN_MESSAGE = "Invalid or expired access token.";

    private final HandlerExceptionResolver handlerExceptionResolver;

    public StaccAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        // Hand over to the shared error handling so the body matches every other API error.
        // A token was sent but could not be accepted, or no token was sent at all.
        String message = authException instanceof OAuth2AuthenticationException
                ? INVALID_TOKEN_MESSAGE
                : AUTHENTICATION_REQUIRED_MESSAGE;
        ApiException failure = new ApiException(HttpStatus.UNAUTHORIZED, message);
        if (handlerExceptionResolver.resolveException(request, response, null, failure) == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
