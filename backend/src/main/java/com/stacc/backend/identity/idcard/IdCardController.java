package com.stacc.backend.identity.idcard;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.identity.idcard.IdCardService.IdCard;
import com.stacc.backend.identity.idcard.IdCardService.IdCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's digital ID card, and the staff check of an ID code. */
@RestController
@Tag(name = "Digital ID")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class IdCardController {

    public record CodeRequest(@NotBlank @Size(max = 40) String code) {
    }

    private final IdCardService cards;

    public IdCardController(IdCardService cards) {
        this.cards = cards;
    }

    @PostMapping("/api/v1/me/id-card")
    @PreAuthorize("hasAnyRole('STUDENT', 'FACULTY', 'ADMIN', 'HOD', 'WARDEN', 'SECURITY', 'MAINTENANCE', 'LIBRARIAN', 'PLACEMENT_OFFICER') "
            + "and hasAuthority('PERSON_PROFILE_VIEW_OWN')")
    @Operation(summary = "Open your digital ID card",
            description = "Your name, login ID, roles, and program or department, with a code valid for 90 seconds. Each call makes a "
                    + "new code; earlier ones stop working.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public IdCard card(@AuthenticationPrincipal Jwt token) {
        return cards.card(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/api/v1/id-cards/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY', 'LIBRARIAN', 'WARDEN') and hasAuthority('ID_CARD_VERIFY')")
    @Operation(summary = "Check a digital ID code", description = "Shows whose card it is while the code is valid. Only reads.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public IdCheck check(@Valid @RequestBody CodeRequest request) {
        return cards.check(request.code());
    }
}
