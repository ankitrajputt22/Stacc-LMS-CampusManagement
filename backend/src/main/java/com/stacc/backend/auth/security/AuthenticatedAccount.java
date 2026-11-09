package com.stacc.backend.auth.security;

import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads who is signed in from the access token of the current request. An endpoint that acts
 * for the caller takes the account from here, never from an ID the client sends.
 */
public final class AuthenticatedAccount {

    private AuthenticatedAccount() {
    }

    /**
     * The ID of the account the access token was issued to. Every Stacc token carries one as a
     * whole number. Anything else is treated as an unacceptable token and answered with 401.
     */
    public static Long idFrom(Jwt accessToken) {
        Object accountId = accessToken == null ? null : accessToken.getClaim(AccessTokenService.ACCOUNT_ID_CLAIM);
        if (accountId instanceof Long || accountId instanceof Integer) {
            return ((Number) accountId).longValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, StaccAuthenticationEntryPoint.INVALID_TOKEN_MESSAGE);
    }
}
