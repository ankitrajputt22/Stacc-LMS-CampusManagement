package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.DepartmentResponse;
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
 * Admin management of departments. Reading needs the admin role and {@code ACADEMIC_MASTER_VIEW}; creating and editing also need
 * {@code ACADEMIC_MASTER_MANAGE}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/departments")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminDepartmentController {

    private final DepartmentManagementService service;

    public AdminDepartmentController(DepartmentManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List departments",
            description = "Returns every department by code, active and inactive.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The departments. The list may be empty.")
    public List<DepartmentResponse> list() {
        return service.list();
    }

    @GetMapping("/{departmentId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one department", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The department.")
    @ApiResponse(
            responseCode = "404",
            description = "The department does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DepartmentResponse get(@PathVariable Long departmentId) {
        return service.get(departmentId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a department",
            description = "Creates an active department. The code is stored in uppercase and can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new department.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A department with this code or name already exists.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DepartmentResponse create(@Valid @RequestBody DepartmentCreate request) {
        return service.create(request);
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a department",
            description = "Changes the name and status. The code stays as it is, and the programs, courses, and people of the department are not touched.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed department.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The department does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "Another department already has this name.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DepartmentResponse update(@PathVariable Long departmentId, @Valid @RequestBody DepartmentUpdate request) {
        return service.update(departmentId, request);
    }
}
