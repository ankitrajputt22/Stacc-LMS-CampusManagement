package com.stacc.backend.auth.api;

import com.stacc.backend.auth.password.PasswordService;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.auth.security.SessionCookies;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one college sign-in, the current session, and signing out. A browser gets its token in the HttpOnly session
 * cookie and never sees it; signing out ends the session on the server, so the token stops working everywhere at once.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final LoginService loginService;
    private final SessionService sessions;
    private final PasswordService passwords;
    private final com.stacc.backend.auth.throttle.SignInThrottleProperties throttleProperties;

    public AuthController(LoginService loginService, SessionService sessions, PasswordService passwords,
            com.stacc.backend.auth.throttle.SignInThrottleProperties throttleProperties) {
        this.loginService = loginService;
        this.sessions = sessions;
        this.passwords = passwords;
        this.throttleProperties = throttleProperties;
    }

    @PostMapping("/login")
    @Operation(
            summary = "College login",
            description = "Checks a college-issued login ID and password, starts a session, and sets it in an HttpOnly "
                    + "cookie. The account's roles come from the college's records and are never chosen by the user.")
    @ApiResponse(responseCode = "200", description = "Signed in; the session cookie is set.")
    @ApiResponse(responseCode = "400", description = "The request is missing a field or is malformed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "The login ID or password is wrong, or a temporary password has "
            + "run out. The message is the same in every case.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Too many failed sign-ins for this login ID or from this address; "
            + "try again after a short wait. Accounts are never locked.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        LoginService.SignIn signIn = loginService.login(request, userAgent, ClientAddress.of(httpRequest,
                throttleProperties.trustProxy()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.create(signIn.token(), signIn.lifetime(),
                        sessions.properties().secureCookie()).toString())
                .body(signIn.session());
    }

    @GetMapping("/session")
    @Operation(summary = "Who is signed in", description = "Answers 401 when no session is open.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The signed-in account and when the session ends at the latest.")
    public SessionResponse session(@AuthenticationPrincipal Jwt token) {
        List<String> roles = token.getClaimAsStringList(AccessTokenService.AUTHORITIES_CLAIM).stream()
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        Long accountId = AuthenticatedAccount.idFrom(token);
        return new SessionResponse(accountId, token.getSubject(), roles, token.getExpiresAt(),
                passwords.isChangeRequired(accountId));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out in this browser", description = "Ends this session and clears its cookie.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Signed out.")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt token) {
        sessions.end(token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM), AuthenticatedAccount.idFrom(token),
                SessionEndReason.SIGNED_OUT);
        return signedOut();
    }

    @PostMapping("/logout-everywhere")
    @Operation(summary = "Sign out in every browser",
            description = "Ends every open session of this account, on every device, and clears this browser's cookie.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Signed out everywhere.")
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal Jwt token) {
        sessions.endAll(AuthenticatedAccount.idFrom(token), SessionEndReason.SIGNED_OUT_EVERYWHERE);
        return signedOut();
    }

    private ResponseEntity<Void> signedOut() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.clear(sessions.properties().secureCookie()).toString())
                .build();
    }
}
