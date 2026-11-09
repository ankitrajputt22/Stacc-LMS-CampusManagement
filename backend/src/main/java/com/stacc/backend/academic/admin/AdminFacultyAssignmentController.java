package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentCreate;
import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.FacultyLookupResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Who teaches each course offering, managed by admins with the faculty assignment permissions. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/academic")
@Tag(name = "Admin faculty assignments")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminFacultyAssignmentController {

    private final FacultyAssignmentService service;

    public AdminFacultyAssignmentController(FacultyAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/offerings/{courseOfferingId}/faculty")
    @PreAuthorize(AdminAcademicAccess.FACULTY_ASSIGNMENT_VIEW)
    @Operation(summary = "Who teaches an offering", description = "Every assignment, active first, with each role.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The assignments. The list may be empty.")
    @ApiResponse(responseCode = "404", description = "The offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<OfferingFaculty> forOffering(@PathVariable Long courseOfferingId) {
        return service.forOffering(courseOfferingId);
    }

    @GetMapping("/faculty")
    @PreAuthorize(AdminAcademicAccess.FACULTY_ASSIGNMENT_VIEW)
    @Operation(summary = "Find faculty members",
            description = "Faculty profiles whose login ID contains the text, by login ID, at most 50. Only the profile ID, "
                    + "login ID, name, department code, and profile status are returned.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The matching faculty members. The list may be empty.")
    public List<FacultyLookupResponse> lookup(
            @RequestParam(required = false) @Size(max = 100, message = "Use at most 100 characters") String q) {
        return service.lookup(q);
    }

    @PostMapping("/faculty-assignments")
    @PreAuthorize(AdminAcademicAccess.FACULTY_ASSIGNMENT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a faculty member to an offering",
            description = "The faculty member then has attendance, results, and LMS access for the offering. An earlier "
                    + "inactive assignment of the same pair is made active again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The assignment.")
    @ApiResponse(responseCode = "404", description = "The offering or faculty profile does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The offering is closed, the faculty member is inactive, not faculty, "
            + "or already assigned, or the offering already has a primary faculty member.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OfferingFaculty assign(@Valid @RequestBody FacultyAssignmentCreate request) {
        return service.assign(request);
    }

    @PutMapping("/faculty-assignments/{teachingAssignmentId}")
    @PreAuthorize(AdminAcademicAccess.FACULTY_ASSIGNMENT_MANAGE)
    @Operation(summary = "Change a faculty assignment",
            description = "Changes the role, or makes the assignment active or inactive. Nothing is deleted.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed assignment.")
    @ApiResponse(responseCode = "404", description = "The assignment does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The offering is closed, the faculty member is inactive, or the "
            + "offering already has a primary faculty member.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OfferingFaculty update(@PathVariable Long teachingAssignmentId,
            @Valid @RequestBody FacultyAssignmentUpdate request) {
        return service.update(teachingAssignmentId, request);
    }
}
