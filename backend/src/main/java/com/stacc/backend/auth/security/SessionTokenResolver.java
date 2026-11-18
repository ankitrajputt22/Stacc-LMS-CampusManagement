package com.stacc.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class SessionTokenResolver implements BearerTokenResolver {

    static final String LOGIN_PATH = "/api/v1/auth/login";

    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        return isLogin(request) ? null : header.resolve(request);
    }

    static boolean usesSessionCookie(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION) == null && SessionCookies.read(request) != null;
    }

    static boolean isLogin(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());
    }
}
