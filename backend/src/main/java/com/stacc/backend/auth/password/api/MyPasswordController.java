package com.stacc.backend.auth.password.api;

import com.stacc.backend.auth.password.PasswordPolicy;
import com.stacc.backend.auth.password.PasswordService;
import com.stacc.backend.auth.security.AuthenticatedAccount;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person changes their own password. It needs only a signed-in session, with no role or permission,
 * because it must also work while the college requires a new password and every other route is closed.
 */
@RestController
@RequestMapping("/api/v1/me/password")
@Tag(name = "My password")
public class MyPasswordController {

    private final PasswordService passwords;

    public MyPasswordController(PasswordService passwords) {
        this.passwords = passwords;
    }

    @Schema(description = "The current password and the new one. Both are sent exactly as typed and never stored.")
    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") @Size(max = 256) String currentPassword,
            @NotBlank(message = "New password is required") @Size(max = PasswordPolicy.MAX_LENGTH * 2) String newPassword) {

        // A record's default text form would include both passwords.
        @Override
        public String toString() {
            return "ChangePasswordRequest[hidden]";
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change my password",
            description = "Checks the current password and the new one against the password policy, then signs every "
                    + "other device out. This session stays signed in.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Changed.")
    @ApiResponse(responseCode = "400", description = "The current password is wrong, or the new one is not acceptable.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> change(@AuthenticationPrincipal Jwt token, @Valid @RequestBody ChangePasswordRequest request) {
        passwords.changeOwnPassword(AuthenticatedAccount.idFrom(token),
                token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM), request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
