package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.AcademicProfileRecords.FacultyProfileSummary;
import com.stacc.backend.academic.admin.AcademicProfileRecords.FacultyProfileUpdate;
import com.stacc.backend.academic.admin.AcademicProfileRecords.StudentProfileSummary;
import com.stacc.backend.academic.admin.AcademicProfileRecords.StudentProfileUpdate;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The official academic mapping of students and faculty, seen and corrected by admins. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/academic/profiles")
@Tag(name = "Admin academic profiles")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminAcademicProfileController {

    private final AcademicProfileAdminService service;

    public AdminAcademicProfileController(AcademicProfileAdminService service) {
        this.service = service;
    }

    @GetMapping("/students")
    @PreAuthorize(AdminAcademicAccess.PROFILE_VIEW)
    @Operation(summary = "Find students",
            description = "Students whose login ID or roll number contains the text, at most 50, with program, department, "
                    + "admission session, and where they study now. Never contact details.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The matching students. The list may be empty.")
    public List<StudentProfileSummary> students(
            @RequestParam(required = false) @Size(max = 100, message = "Use at most 100 characters") String q) {
        return service.students(q);
    }

    @PutMapping("/students/{studentProfileId}")
    @PreAuthorize(AdminAcademicAccess.PROFILE_MANAGE)
    @Operation(summary = "Change a student's roll number or profile status",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student.")
    @ApiResponse(responseCode = "404", description = "The student profile does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Another student already has this roll number.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public StudentProfileSummary updateStudent(@PathVariable Long studentProfileId,
            @Valid @RequestBody StudentProfileUpdate request) {
        return service.updateStudent(studentProfileId, request);
    }

    @GetMapping("/faculty")
    @PreAuthorize(AdminAcademicAccess.PROFILE_VIEW)
    @Operation(summary = "Find faculty members",
            description = "Faculty whose login ID contains the text, at most 50, with department and how many offerings "
                    + "they teach now. Never contact details.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The matching faculty members. The list may be empty.")
    public List<FacultyProfileSummary> faculty(
            @RequestParam(required = false) @Size(max = 100, message = "Use at most 100 characters") String q) {
        return service.faculty(q);
    }

    @PutMapping("/faculty/{facultyProfileId}")
    @PreAuthorize(AdminAcademicAccess.PROFILE_MANAGE)
    @Operation(summary = "Change a faculty member's primary department or profile status",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The faculty member.")
    @ApiResponse(responseCode = "400", description = "The department does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The faculty profile does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The department is inactive.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public FacultyProfileSummary updateFaculty(@PathVariable Long facultyProfileId,
            @Valid @RequestBody FacultyProfileUpdate request) {
        return service.updateFaculty(facultyProfileId, request);
    }
}
