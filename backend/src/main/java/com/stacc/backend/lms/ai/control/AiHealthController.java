package com.stacc.backend.lms.ai.control;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Operations")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiHealthController {

    private final AiHealthService health;

    public AiHealthController(AiHealthService health) {
        this.health = health;
    }

    @GetMapping("/api/v1/admin/system/ai")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('SYSTEM_HEALTH_VIEW')")
    @Operation(summary = "AI health",
            description = "Whether AI is on and the AI service answers, index and job states, requests running and waiting "
                    + "now, and the last 24 hours of AI requests by outcome and kind. Numbers only: no settings, courses, "
                    + "people, or content.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The AI health.")
    public AiHealthService.AiHealth ai() {
        return health.now();
    }
}
