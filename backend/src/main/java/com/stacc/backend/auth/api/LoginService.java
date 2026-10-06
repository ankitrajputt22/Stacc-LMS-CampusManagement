package com.stacc.backend.auth.api;

import com.stacc.backend.auth.security.StaccUserPrincipal;
import com.stacc.backend.common.error.ApiException;
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

    public LoginService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * Checks the login ID and password through Spring Security. Nothing is stored on the
     * server afterwards: no session is created and no token is issued yet.
     */
    public LoginResponse login(LoginRequest request) {
        StaccUserPrincipal principal = (StaccUserPrincipal) authenticate(request).getPrincipal();
        return new LoginResponse(principal.getAccountId(), principal.getLoginId(), principal.getRoleNames());
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
