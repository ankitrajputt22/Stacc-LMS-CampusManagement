package com.stacc.backend.notification.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.notification.EmailDelivery;
import com.stacc.backend.notification.NotificationCategory;
import com.stacc.backend.notification.NotificationPreferenceService;
import com.stacc.backend.notification.NotificationPreferenceService.Choice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own notification delivery choices. */
@RestController
@RequestMapping("/api/v1/notifications/settings")
@Tag(name = "Notification settings")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account cannot use notifications.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class NotificationSettingsController {

    private static final String ACCESS = "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN') and hasAuthority('NOTIFICATION_VIEW')";

    @Schema(description = "One category's choice.")
    public record ChoiceBody(@NotNull NotificationCategory category, boolean inApp, @NotNull EmailDelivery email) {
    }

    @Schema(description = "Choices to save; categories left out keep theirs.")
    public record SettingsRequest(@NotNull @Size(max = 10) List<@Valid ChoiceBody> choices) {
    }

    @Schema(description = "Every category's choice, and whether email is switched on for the college at all.")
    public record SettingsResponse(boolean emailAvailable, List<ChoiceBody> choices) {
    }

    private final NotificationPreferenceService settings;

    public NotificationSettingsController(NotificationPreferenceService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize(ACCESS)
    @Operation(summary = "Read your notification settings", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public SettingsResponse read(@AuthenticationPrincipal Jwt token) {
        return response(settings.choices(AuthenticatedAccount.idFrom(token)));
    }

    @PutMapping
    @PreAuthorize(ACCESS)
    @Operation(summary = "Change your notification settings",
            description = "Per category: whether it shows in the Inbox, and whether it is emailed at once, daily, or not at all. "
                    + "Emails go to the address the college recorded, only while email is switched on for the college.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public SettingsResponse change(@AuthenticationPrincipal Jwt token, @Valid @RequestBody SettingsRequest request) {
        return response(settings.choose(AuthenticatedAccount.idFrom(token), request.choices().stream()
                .map(choice -> new Choice(choice.category(), choice.inApp(), choice.email())).toList()));
    }

    private SettingsResponse response(List<Choice> choices) {
        return new SettingsResponse(settings.emailAvailable(),
                choices.stream().map(choice -> new ChoiceBody(choice.category(), choice.inApp(), choice.email())).toList());
    }
}
