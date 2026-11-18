package com.stacc.backend.auth.security;

import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthenticatedAccount {

    private AuthenticatedAccount() {
    }

    public static Long idFrom(Jwt accessToken) {
        Object accountId = accessToken == null ? null : accessToken.getClaim(AccessTokenService.ACCOUNT_ID_CLAIM);
        if (accountId instanceof Long || accountId instanceof Integer) {
            return ((Number) accountId).longValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, StaccAuthenticationEntryPoint.INVALID_TOKEN_MESSAGE);
    }
}
