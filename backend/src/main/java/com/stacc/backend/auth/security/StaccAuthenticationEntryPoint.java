package com.stacc.backend.auth.security;

import java.io.IOException;

import com.stacc.backend.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers a request whose access token cannot be accepted with HTTP 401 in the common
 * error format. The reason a token was refused is never sent to the client.
 */
@Component
public class StaccAuthenticationEntryPoint implements AuthenticationEntryPoint {

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
        ApiException failure = new ApiException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
        if (handlerExceptionResolver.resolveException(request, response, null, failure) == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
