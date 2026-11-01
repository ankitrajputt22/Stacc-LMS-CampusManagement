package com.stacc.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request one ID that ties together what the person saw and what the server logged. The ID is sent back in
 * the {@value #HEADER} response header and in every error body, and appears in each log line written for the request.
 *
 * <p>An ID that arrives with the request is kept when it looks like one (Nginx sends its own for every request), so the
 * proxy's and the backend's logs match; anything else is replaced by a new random ID. It is only a reference for
 * finding log lines: it never grants or decides anything, and holds no personal data.
 *
 * <p>Runs before every other filter, Spring Security included, so even a refused request has its ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    /** The key of the ID in the logging context and in the request attributes. */
    public static final String KEY = "requestId";

    // Letters, digits, dots, dashes, and underscores, 8 to 64 of them: enough for UUIDs and Nginx's hex IDs, and safe to
    // write into a header or a log line as they are.
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = acceptable(request.getHeader(HEADER));
        request.setAttribute(KEY, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // An error dispatch keeps the ID of the request it belongs to.
        return true;
    }

    /** The ID of this request, as set by this filter; null outside a request it handled. */
    public static String of(HttpServletRequest request) {
        Object value = request.getAttribute(KEY);
        return value instanceof String id ? id : null;
    }

    static String acceptable(String incoming) {
        if (incoming != null && ACCEPTABLE.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
