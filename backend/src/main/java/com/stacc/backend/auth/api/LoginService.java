package com.stacc.backend.auth.api;

import com.stacc.backend.auth.security.StaccUserPrincipal;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.session.UserSession;
import com.stacc.backend.auth.token.AccessToken;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.error.ApiException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    // The same message for every failed login, so a caller cannot tell which part was wrong.
    static final String INVALID_LOGIN_MESSAGE = "Invalid login ID or password.";

    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final SessionService sessions;

    public LoginService(AuthenticationManager authenticationManager, AccessTokenService accessTokenService,
            SessionService sessions) {
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.sessions = sessions;
    }

    /** A successful sign-in: what the page may see, and the token for the session cookie, which it may not. */
    public record SignIn(SessionResponse session, String token, Duration lifetime) {

        // A record's default text form would include the token, so it is left out here.
        @Override
        public String toString() {
            return "SignIn[" + session + "]";
        }
    }

    /**
     * Checks the login ID and password through Spring Security and, only when they are correct, starts a session and
     * issues its token.
     */
    public SignIn login(LoginRequest request, String userAgent) {
        StaccUserPrincipal principal = (StaccUserPrincipal) authenticate(request).getPrincipal();
        UserSession session = sessions.start(principal.getAccountId(), userAgent);
        AccessToken token = accessTokenService.issue(principal, session.getId(), session.getExpiresAt());
        return new SignIn(
                new SessionResponse(principal.getAccountId(), principal.getLoginId(), principal.getRoleNames(),
                        session.getExpiresAt()),
                token.value(),
                Duration.ofSeconds(token.expiresInSeconds()));
    }

    private Authentication authenticate(LoginRequest request) {
        try {
            // The password is passed on exactly as it was sent.
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.loginId(), request.password()));
        } catch (BadCredentialsException | AccountStatusException exception) {
            // Wrong password, unknown login ID, or a disabled account. Any other failure is a
            // real server problem and is deliberately not caught here.
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
        }
    }
}
