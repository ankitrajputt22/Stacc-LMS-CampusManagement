package com.stacc.backend.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedAccountTest {

    private static Jwt tokenWithAccountId(Object accountId) {
        Jwt.Builder token = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("TEST-STUDENT-1");
        if (accountId != null) {
            token.claim("accountId", accountId);
        }
        return token.build();
    }

    private static void expectRefused(Jwt token) {
        ApiException exception = assertThrows(ApiException.class, () -> AuthenticatedAccount.idFrom(token));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Invalid or expired access token.", exception.getMessage());
    }

    @Test
    void accountIdIsReadWhicheverWholeNumberTypeTheTokenHolds() {
        assertEquals(12L, AuthenticatedAccount.idFrom(tokenWithAccountId(12L)));
        assertEquals(12L, AuthenticatedAccount.idFrom(tokenWithAccountId(12)));
        assertEquals(5_000_000_000L, AuthenticatedAccount.idFrom(tokenWithAccountId(5_000_000_000L)));
    }

    @Test
    void tokenWithoutAUsableAccountIdIsRefusedAsUnauthorized() {
        expectRefused(tokenWithAccountId(null));
        expectRefused(tokenWithAccountId("12"));
        expectRefused(tokenWithAccountId("not-a-number"));
        expectRefused(tokenWithAccountId(12.5));
        expectRefused(tokenWithAccountId(true));
        expectRefused(null);
    }
}
