package com.stacc.backend.auth.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only in test code. It lets security tests see who Spring Security thinks is
 * calling, without adding any such endpoint to the real application.
 */
@RestController
@RequestMapping("/api/test")
public class SecurityTestController {

    @GetMapping("/whoami")
    public Map<String, Object> whoAmI(Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (authentication == null) {
            result.put("authenticated", false);
            return result;
        }
        result.put("authenticated", authentication.isAuthenticated());
        result.put("name", authentication.getName());
        result.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList());
        if (authentication instanceof JwtAuthenticationToken token) {
            result.put("accountId", token.getToken().getClaim("accountId"));
        }
        return result;
    }
}
