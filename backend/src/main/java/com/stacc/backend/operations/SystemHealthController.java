package com.stacc.backend.operations;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.operations.SystemHealthService.SystemHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admins' system health page. */
@RestController
@Tag(name = "Operations")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class SystemHealthController {

    private final SystemHealthService service;

    public SystemHealthController(SystemHealthService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/system")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('SYSTEM_HEALTH_VIEW')")
    @Operation(summary = "System health", description = "Each part's status, the schema version, optional features, and the JVM's uptime and memory. "
            + "No addresses, credentials, or settings.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public SystemHealth health() {
        return service.now();
    }
}
