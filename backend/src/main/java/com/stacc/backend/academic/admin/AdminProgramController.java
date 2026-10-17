package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.ProgramResponse;
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
 * Admin management of programs. A program belongs to one department, named by its ID and loaded on the server. Reading needs the admin role and {@code ACADEMIC_MASTER_VIEW}; creating and editing also need
 * {@code ACADEMIC_MASTER_MANAGE}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/programs")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminProgramController {

    private final ProgramManagementService service;

    public AdminProgramController(ProgramManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List programs",
            description = "Returns every program with its department, by department code and then program code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The programs. The list may be empty.")
    public List<ProgramResponse> list() {
        return service.list();
    }

    @GetMapping("/{programId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one program", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The program.")
    @ApiResponse(
            responseCode = "404",
            description = "The program does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ProgramResponse get(@PathVariable Long programId) {
        return service.get(programId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a program",
            description = "Creates an active program in an existing department. The code is stored in uppercase; the department and code can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new program.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, or the department does not exist. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The department already has a program with this code or name.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ProgramResponse create(@Valid @RequestBody ProgramCreate request) {
        return service.create(request);
    }

    @PutMapping("/{programId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a program",
            description = "Changes the name, duration, and status. The department and code stay as they are. The duration cannot drop below the highest semester the program already has.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed program.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The program does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The department already has a program with this name, or the program already has a semester beyond the new duration.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ProgramResponse update(@PathVariable Long programId, @Valid @RequestBody ProgramUpdate request) {
        return service.update(programId, request);
    }
}
