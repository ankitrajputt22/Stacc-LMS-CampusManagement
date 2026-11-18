package com.stacc.backend.auth.security;

import com.stacc.backend.auth.session.SessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

    private SessionCookies() {
    }

    public static ResponseCookie create(String token, Duration lifetime, boolean secure) {
        return base(token, secure).maxAge(lifetime).build();
    }

    public static ResponseCookie clear(boolean secure) {
        return base("", secure).maxAge(Duration.ZERO).build();
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionProperties.COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
        return ResponseCookie.from(SessionProperties.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(SessionProperties.COOKIE_PATH);
    }
}
