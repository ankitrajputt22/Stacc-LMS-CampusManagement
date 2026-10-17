package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.CourseResponse;
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
 * Admin management of the permanent course catalog. A course has one owning department, named by its ID and loaded on the server; semester offerings are managed separately. Reading needs the admin role and {@code ACADEMIC_MASTER_VIEW}; creating and editing also need
 * {@code ACADEMIC_MASTER_MANAGE}. There is no delete.
 */
@RestController
@RequestMapping("/api/admin/academic/courses")
@Tag(name = "Admin academic masters")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminCourseController {

    private final CourseManagementService service;

    public AdminCourseController(CourseManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(
            summary = "List courses",
            description = "Returns the whole catalog with each owning department, by course code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The courses. The list may be empty.")
    public List<CourseResponse> list() {
        return service.list();
    }

    @GetMapping("/{courseId}")
    @PreAuthorize(AdminAcademicAccess.VIEW)
    @Operation(summary = "Get one course", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course.")
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseResponse get(@PathVariable Long courseId) {
        return service.get(courseId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a course",
            description = "Creates an active course. The code is stored in uppercase and is unique across the college; the department and code can never be changed afterwards.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new course.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, such as credits with more than two decimal places, or the department does not exist. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A course with this code already exists.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseResponse create(@Valid @RequestBody CourseCreate request) {
        return service.create(request);
    }

    @PutMapping("/{courseId}")
    @PreAuthorize(AdminAcademicAccess.MANAGE)
    @Operation(
            summary = "Edit a course",
            description = "Changes the name, credits, and status. The department and code stay as they are, and the course’s offerings are not touched.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed course.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The change clashes with stored data.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseResponse update(@PathVariable Long courseId, @Valid @RequestBody CourseUpdate request) {
        return service.update(courseId, request);
    }
}
