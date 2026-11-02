package com.stacc.backend.auth.session.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.session.UserSession;
import com.stacc.backend.auth.token.AccessTokenService;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person's own sign-ins: where they are signed in, and signing out one other device. Like signing out,
 * these need only a signed-in session, and the account always comes from the access token.
 */
@RestController
@RequestMapping("/api/v1/me/sessions")
@Tag(name = "My sessions")
@ApiResponse(responseCode = "401", description = "No valid session.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class MySessionsController {

    static final String NOT_FOUND = "Session not found.";
    static final String CURRENT = "This is the session you are using. Sign out to end it.";

    private final SessionService sessions;

    public MySessionsController(SessionService sessions) {
        this.sessions = sessions;
    }

    public record MySession(
            @Schema(description = "Identifies the session for ending it.") String sessionId,
            @Schema(description = "A short description of the browser and device, such as \"Chrome on Android\".",
                    example = "Chrome on Android") String device,
            Instant signedInAt,
            Instant lastSeenAt,
            @Schema(description = "When it ends at the latest.") Instant expiresAt,
            @Schema(description = "Whether this is the session making the request.") boolean current) {
    }

    @GetMapping
    @Operation(summary = "My open sessions", description = "Every open sign-in of the account, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The open sessions, the current one included.")
    public List<MySession> list(@AuthenticationPrincipal Jwt token) {
        String current = token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM);
        return sessions.openSessions(AuthenticatedAccount.idFrom(token)).stream()
                .map(session -> new MySession(session.getId(), session.getDevice(), session.getCreatedAt(),
                        session.getLastSeenAt(), session.getExpiresAt(), session.getId().equals(current)))
                .toList();
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "Sign out one other session",
            description = "Ends one other open session of the account at once. The current session is ended with sign "
                    + "out instead.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "The session has ended.")
    @ApiResponse(responseCode = "404", description = "No such open session of this account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "It is the current session.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> end(@AuthenticationPrincipal Jwt token, @PathVariable String sessionId) {
        Long accountId = AuthenticatedAccount.idFrom(token);
        if (Objects.equals(sessionId, token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM))) {
            throw new ApiException(HttpStatus.CONFLICT, CURRENT);
        }
        boolean open = sessions.openSessions(accountId).stream().map(UserSession::getId).anyMatch(sessionId::equals);
        if (!open) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        sessions.end(sessionId, accountId, SessionEndReason.SIGNED_OUT);
        return ResponseEntity.noContent().build();
    }
}
