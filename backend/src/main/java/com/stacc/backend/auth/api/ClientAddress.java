package com.stacc.backend.auth.api;

import jakarta.servlet.http.HttpServletRequest;

final class ClientAddress {

    private static final int MAX_LENGTH = 45;

    private ClientAddress() {
    }

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
