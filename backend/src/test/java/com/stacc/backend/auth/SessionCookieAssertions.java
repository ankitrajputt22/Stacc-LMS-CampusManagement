package com.stacc.backend.auth;

import com.stacc.backend.auth.session.SessionProperties;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/** Finds the session cookie among an answer's Set-Cookie headers (the XSRF-TOKEN cookie may be there too). */
public final class SessionCookieAssertions {

    private SessionCookieAssertions() {
    }

    /** The whole Set-Cookie value of the session cookie, or null. */
    public static String sessionCookie(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return cookies.stream().filter(cookie -> cookie.startsWith(SessionProperties.COOKIE_NAME + "=")).findFirst()
                .orElse(null);
    }

    /** The token inside the session cookie. */
    public static String sessionToken(MvcResult result) {
        String cookie = sessionCookie(result);
        return cookie == null ? null : cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    public static ResultMatcher noSessionCookie() {
        return result -> {
            if (sessionCookie(result) != null) {
                throw new AssertionError("Expected no session cookie, but one was set");
            }
        };
    }
}
