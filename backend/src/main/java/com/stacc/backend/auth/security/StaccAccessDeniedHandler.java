package com.stacc.backend.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers a signed-in account that is not allowed to do something with HTTP 403 in the
 * common error format. Which rule refused the request is never sent to the client.
 */
@Component
public class StaccAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public StaccAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        // Hand over to the shared error handling, which answers every refused request the same way.
        if (handlerExceptionResolver.resolveException(request, response, null, accessDeniedException) == null) {
            response.sendError(HttpStatus.FORBIDDEN.value());
        }
    }
}
