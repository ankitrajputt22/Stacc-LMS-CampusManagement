package com.stacc.backend.audit;

import java.time.Instant;

import com.stacc.backend.audit.AuditQueryService.AuditPage;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The read-only audit log for admins. There is no route that writes, changes, or deletes an event. */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "Admin audit log")
public class AdminAuditController {

    private final AuditQueryService queries;

    public AdminAuditController(AuditQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('AUDIT_LOG_VIEW')")
    @Operation(summary = "Read the audit log",
            description = "One page of recorded sensitive actions, newest first, filtered by action and by a range of "
                    + "moments (from included, to excluded), and searched by the login ID of the actor or of a target "
                    + "account. Read-only.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The page; it may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is invalid or the page is out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not an admin with AUDIT_LOG_VIEW.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AuditPage search(
            @RequestParam(required = false) AuditAction action,
            @Parameter(description = "An ISO 8601 moment with a zone.") @RequestParam(required = false) Instant from,
            @Parameter(description = "An ISO 8601 moment with a zone.") @RequestParam(required = false) Instant to,
            @Parameter(description = "Part of a login ID.") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.search(action, from, to, q, page, size);
    }
}
