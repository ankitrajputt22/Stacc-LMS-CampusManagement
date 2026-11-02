package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentSectionChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterCourseEnrollmentsResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentLookupResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentSemesterEnrollmentsResponse;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Admin management of official enrollments: find a student, enroll them in a semester of their program and in that
 * semester's course offerings, and end those enrollments. Reading needs the admin role and
 * {@code ACADEMIC_ENROLLMENT_VIEW}; changing also needs {@code ACADEMIC_ENROLLMENT_MANAGE}. The route always names
 * the student or enrollment; no body carries it. There is no delete, and no account or profile is changed.
 */
@RestController
@RequestMapping("/api/v1/admin/academic/enrollments")
@Tag(name = "Admin enrollments")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminEnrollmentController {

    private final AdminEnrollmentQueryService queries;
    private final AcademicEnrollmentManagementService enrollments;

    public AdminEnrollmentController(AdminEnrollmentQueryService queries, AcademicEnrollmentManagementService enrollments) {
        this.queries = queries;
        this.enrollments = enrollments;
    }

    @GetMapping("/students")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_VIEW)
    @Operation(
            summary = "Find students to enroll",
            description = "Returns at most 50 student profiles whose login ID contains the text, ignoring case, by login ID. Without text the first 50 are listed. Only the profile ID, login ID, program, admission session, and profile status are returned. It is read-only and not a user directory.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The matching students. The list may be empty.")
    @ApiResponse(
            responseCode = "400",
            description = "The search text is longer than 100 characters.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<StudentLookupResponse> searchStudents(
            @Parameter(description = "Part of a student’s login ID.", example = "STU-2026") @RequestParam(required = false) String q) {
        return queries.searchStudents(q);
    }

    @GetMapping("/students/{studentProfileId}/semesters")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_VIEW)
    @Operation(
            summary = "List a student’s semester enrollments",
            description = "Returns the student and every semester enrollment they have had, whatever its status: the latest session first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student and their semester enrollments. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The student does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public StudentSemesterEnrollmentsResponse studentSemesterEnrollments(@PathVariable Long studentProfileId) {
        return queries.studentSemesterEnrollments(studentProfileId);
    }

    @PostMapping("/students/{studentProfileId}/semesters")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Enroll a student in a semester",
            description = "Creates an ENROLLED semester enrollment for an active student in a semester of their own program. The status cannot be chosen. It gives no course or LMS access by itself.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new semester enrollment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The student or the selected semester does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The student is already enrolled in this semester, the student profile is inactive, or the semester belongs to another program.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterEnrollmentResponse createSemesterEnrollment(@PathVariable Long studentProfileId,
            @Valid @RequestBody SemesterEnrollmentCreate request) {
        return enrollments.createSemesterEnrollment(studentProfileId, request);
    }

    @PutMapping("/semester-enrollments/{semesterEnrollmentId}/status")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_MANAGE)
    @Operation(
            summary = "Change a semester enrollment’s status",
            description = "An ENROLLED semester enrollment may become COMPLETED or WITHDRAWN once none of its course enrollments is still ENROLLED. Nothing cascades: each course enrollment is ended on its own first. Sending the current status changes nothing. An ended enrollment never changes again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The semester enrollment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The semester enrollment does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A course enrollment is still ENROLLED, or the semester enrollment has already ended.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterEnrollmentResponse changeSemesterEnrollmentStatus(@PathVariable Long semesterEnrollmentId,
            @Valid @RequestBody SemesterEnrollmentStatusChange request) {
        return enrollments.changeSemesterEnrollmentStatus(semesterEnrollmentId, request);
    }

    @PutMapping("/semester-enrollments/{semesterEnrollmentId}/section")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_MANAGE)
    @Operation(
            summary = "Place a student in a section",
            description = "Places an enrolled student in an active section of their semester, moves them to another, or "
                    + "takes them out with a null section. A full section is refused.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The semester enrollment with its section.")
    @ApiResponse(
            responseCode = "404",
            description = "The semester enrollment or the section does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The enrollment has ended, or the section is of another semester, inactive, or full.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterEnrollmentResponse changeSection(@PathVariable Long semesterEnrollmentId,
            @Valid @RequestBody SemesterEnrollmentSectionChange request) {
        return enrollments.changeSection(semesterEnrollmentId, request);
    }

    @GetMapping("/semester-enrollments/{semesterEnrollmentId}/courses")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_VIEW)
    @Operation(
            summary = "List a semester enrollment’s course enrollments",
            description = "Returns the student, the semester enrollment, and each of its course enrollments, whatever its status, by course code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The semester enrollment and its course enrollments. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The semester enrollment does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SemesterCourseEnrollmentsResponse semesterCourseEnrollments(@PathVariable Long semesterEnrollmentId) {
        return queries.semesterCourseEnrollments(semesterEnrollmentId);
    }

    @PostMapping("/semester-enrollments/{semesterEnrollmentId}/courses")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Enroll a student in a course offering",
            description = "Creates an ENROLLED course enrollment under an ENROLLED semester enrollment, for an offering of the same semester. The status cannot be chosen. While it stays ENROLLED, the student uses the offering's LMS course whenever that course is published; access is worked out live, so nothing else is created.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new course enrollment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The semester enrollment or the selected course offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The student is already enrolled in this offering, the semester enrollment has ended, or the offering belongs to another semester.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseEnrollmentResponse createCourseEnrollment(@PathVariable Long semesterEnrollmentId,
            @Valid @RequestBody CourseEnrollmentCreate request) {
        return enrollments.createCourseEnrollment(semesterEnrollmentId, request);
    }

    @PutMapping("/course-enrollments/{courseEnrollmentId}/status")
    @PreAuthorize(AdminAcademicAccess.ENROLLMENT_MANAGE)
    @Operation(
            summary = "Change a course enrollment’s status",
            description = "An ENROLLED course enrollment may become COMPLETED or WITHDRAWN. Sending the current status changes nothing. An ended enrollment never changes again, and the student loses LMS access to the course at once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course enrollment.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course enrollment does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The course enrollment has already ended.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CourseEnrollmentResponse changeCourseEnrollmentStatus(@PathVariable Long courseEnrollmentId,
            @Valid @RequestBody CourseEnrollmentStatusChange request) {
        return enrollments.changeCourseEnrollmentStatus(courseEnrollmentId, request);
    }
}
