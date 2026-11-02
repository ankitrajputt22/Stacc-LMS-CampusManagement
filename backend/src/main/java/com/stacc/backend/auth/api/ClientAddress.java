package com.stacc.backend.auth.api;

import jakarta.servlet.http.HttpServletRequest;

/** The network address a sign-in comes from, for throttling only. It is never stored as is, logged, or sent back. */
final class ClientAddress {

    private static final int MAX_LENGTH = 45;

    private ClientAddress() {
    }

    /**
     * The address Stacc's own proxy saw when the proxy is trusted ({@code X-Real-IP}, which the proxy sets itself),
     * otherwise the address of the connection. A forwarded header is never read when the backend may be reached
     * directly, because anyone could then choose their address.
     */
    static String of(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank() && real.strip().length() <= MAX_LENGTH) {
                return real.strip();
            }
        }
        return request.getRemoteAddr();
    }
}
