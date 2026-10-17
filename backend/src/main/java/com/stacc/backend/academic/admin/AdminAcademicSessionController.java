package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.AcademicSessionResponse;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management of college-wide academic sessions. Reading needs the admin role and {@code ACADEMIC_MASTER_VIEW}; creating and editing also need
 * {@code ACADEMIC_MASTER_MANAGE}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/sessions")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminAcademicSessionController {

    private final AcademicSessionManagementService service;

    public AdminAcademicSessionController(AcademicSessionManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List academic sessions",
            description = "Returns every academic session, the latest start date first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The academic sessions. The list may be empty.")
    public List<AcademicSessionResponse> list() {
        return service.list();
    }

    @GetMapping("/{academicSessionId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one academic session", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The academic session.")
    @ApiResponse(
            responseCode = "404",
            description = "The academic session does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AcademicSessionResponse get(@PathVariable Long academicSessionId) {
        return service.get(academicSessionId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a academic session",
            description = "Creates a planned session. The end date must be after the start date. The code can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new academic session.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, such as an end date that is not after the start date. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "An academic session with this code already exists.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AcademicSessionResponse create(@Valid @RequestBody AcademicSessionCreate request) {
        return service.create(request);
    }

    @PutMapping("/{academicSessionId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a academic session",
            description = "Changes the dates and status. A session moves from PLANNED to ACTIVE to CLOSED and never back.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed academic session.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The academic session does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The status cannot change that way, such as making a closed session active again.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AcademicSessionResponse update(@PathVariable Long academicSessionId, @Valid @RequestBody AcademicSessionUpdate request) {
        return service.update(academicSessionId, request);
    }
}
