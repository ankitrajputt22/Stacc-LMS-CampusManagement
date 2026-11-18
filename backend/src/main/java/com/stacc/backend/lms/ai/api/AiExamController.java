package com.stacc.backend.lms.ai.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "LMS AI teaching tools")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The blueprint does not exist or is not in a course this faculty member teaches.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiExamController {

    private final AiExamShortfallService shortfall;

    public AiExamController(AiExamShortfallService shortfall) {
        this.shortfall = shortfall;
    }

    @PostMapping("/api/v1/lms/faculty/ai/exam-blueprints/{blueprintId}/fill-shortfall")
    @PreAuthorize(AiAccess.FACULTY_PAPER)
    @Operation(summary = "Draft the questions an exam blueprint is short of",
            description = "For each row the bank is short of, AI drafts up to 10 questions into the bank with the row's type, "
                    + "difficulty, topic, Bloom level, and outcome, as Needs review. Review them, then build the paper.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "What was drafted for each short row.")
    @ApiResponse(responseCode = "409", description = "AI knowledge is not ready for the current content.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI is switched off or unavailable.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiExamShortfallService.Result fill(@PathVariable Long blueprintId, @AuthenticationPrincipal Jwt accessToken) {
        return shortfall.fill(AuthenticatedAccount.idFrom(accessToken), blueprintId);
    }
}
