package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SectionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Student sections of each semester, managed by admins with the academic master permissions. */
@RestController
@RequestMapping("/api/v1/admin/academic/sections")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminSectionController {

    private final SectionManagementService service;

    public AdminSectionController(SectionManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List sections",
            description = "Every section, or one semester's, the latest session first, with how many students are "
                    + "enrolled in each.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The sections. The list may be empty.")
    public List<SectionResponse> list(@RequestParam(required = false) Long semesterId) {
        return service.list(semesterId);
    }

    @GetMapping("/{sectionId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one section", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The section.")
    @ApiResponse(
            responseCode = "404",
            description = "The section does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SectionResponse get(@PathVariable Long sectionId) {
        return service.get(sectionId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a section",
            description = "Creates an active section in a semester that is not closed. The semester and code never change.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new section.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, or the semester does not exist. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The semester is closed, or already has a section with this code.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SectionResponse create(@Valid @RequestBody SectionCreate request) {
        return service.create(request);
    }

    @PutMapping("/{sectionId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a section",
            description = "Changes the name, capacity, and status. The capacity cannot go below the students enrolled in it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed section.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The section does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The capacity would be below the students enrolled in it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SectionResponse update(@PathVariable Long sectionId, @Valid @RequestBody SectionUpdate request) {
        return service.update(sectionId, request);
    }
}
