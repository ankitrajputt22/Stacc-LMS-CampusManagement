package com.stacc.backend.auth.api;

import com.stacc.backend.auth.throttle.SignInThrottleService;
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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LoginService.class);

    // The same message for every failed login, so a caller cannot tell which part was wrong.
    static final String INVALID_LOGIN_MESSAGE = "Invalid login ID or password.";
    // The same message whoever is blocked: it never says whether the login ID exists.
    static final String TOO_MANY_MESSAGE = "Too many sign-in attempts. Wait a few minutes and try again.";

    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final SessionService sessions;
    private final SignInThrottleService throttle;

    public LoginService(AuthenticationManager authenticationManager, AccessTokenService accessTokenService,
            SessionService sessions, SignInThrottleService throttle) {
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.sessions = sessions;
        this.throttle = throttle;
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
     * issues its token. After repeated failures for the login ID or from the address, a try is refused with 429 before
     * the password is even checked; a failure is counted, and a success forgets the login ID's failures.
     *
     * @param address the client's network address, used only as a hashed throttle key and never logged
     */
    public SignIn login(LoginRequest request, String userAgent, String address) {
        if (!throttle.waitFor(request.loginId(), address).isZero()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE);
        }
        Authentication authentication;
        try {
            authentication = authenticate(request);
        } catch (ApiException refused) {
            count(() -> throttle.recordFailure(request.loginId(), address));
            throw refused;
        }
        count(() -> throttle.recordSuccess(request.loginId()));
        StaccUserPrincipal principal = (StaccUserPrincipal) authentication.getPrincipal();
        UserSession session = sessions.start(principal.getAccountId(), userAgent);
        AccessToken token = accessTokenService.issue(principal, session.getId(), session.getExpiresAt());
        return new SignIn(
                new SessionResponse(principal.getAccountId(), principal.getLoginId(), principal.getRoleNames(),
                        session.getExpiresAt(), principal.isPasswordChangeRequired()),
                token.value(),
                Duration.ofSeconds(token.expiresInSeconds()));
    }

    // Counting never changes the answer: a database problem here is logged, without the login ID or address, and the
    // sign-in result stands.
    private static void count(Runnable bookkeeping) {
        try {
            bookkeeping.run();
        } catch (RuntimeException failure) {
            LOGGER.warn("Sign-in throttling could not record an attempt", failure);
        }
    }

    private Authentication authenticate(LoginRequest request) {
        try {
            // The password is passed on exactly as it was sent.
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.loginId(), request.password()));
        } catch (BadCredentialsException | AccountStatusException exception) {
            // Wrong password, unknown login ID, a disabled account, or a temporary password that has run out (checked
            // only after the password matched). Any other failure is a
            // real server problem and is deliberately not caught here.
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
        }
    }
}
