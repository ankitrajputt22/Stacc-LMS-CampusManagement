package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SemesterResponse;
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
 * Admin management of semesters. A semester is one numbered semester of one program in one academic session, both named by ID and loaded on the server. Reading needs the admin role and {@code ACADEMIC_MASTER_VIEW}; creating and editing also need
 * {@code ACADEMIC_MASTER_MANAGE}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/semesters")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminSemesterController {

    private final SemesterManagementService service;

    public AdminSemesterController(SemesterManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List semesters",
            description = "Returns every semester with its program, department, and session: the latest session first, then program code, then semester number.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The semesters. The list may be empty.")
    public List<SemesterResponse> list() {
        return service.list();
    }

    @GetMapping("/{semesterId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one semester", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The semester.")
    @ApiResponse(
            responseCode = "404",
            description = "The semester does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterResponse get(@PathVariable Long semesterId) {
        return service.get(semesterId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a semester",
            description = "Creates a planned semester. The number must not exceed the program’s duration. The program, session, and number can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new semester.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, the program or session does not exist, or the number exceeds the program’s duration. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The program already has this semester in this academic session.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterResponse create(@Valid @RequestBody SemesterCreate request) {
        return service.create(request);
    }

    @PutMapping("/{semesterId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a semester",
            description = "Changes the status only. A semester moves from PLANNED to ACTIVE to CLOSED and never back.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed semester.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The semester does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The status cannot change that way, such as making a closed semester active again.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterResponse update(@PathVariable Long semesterId, @Valid @RequestBody SemesterUpdate request) {
        return service.update(semesterId, request);
    }
}
