package com.stacc.backend.academic.registration;

import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegisteredCourse;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationOverview;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationRequest;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import com.stacc.backend.auth.security.AuthenticatedAccount;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in student's own course registration. The student always comes from the access token; no route takes a
 * student, profile, or account ID.
 */
@RestController
@RequestMapping("/api/v1/students/me")
@Tag(name = "Course registration")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not a student or lacks the permission.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class StudentRegistrationController {

    private final StudentRegistrationService service;

    public StudentRegistrationController(StudentRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/course-registration")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('COURSE_REGISTRATION_VIEW')")
    @Operation(summary = "My course registration",
            description = "Each semester of the current session the student is enrolled in, with its registration "
                    + "period and drop deadline, the offerings open to the student's semester and section (with free "
                    + "places and whether each can be taken now, and why not), and the student's course enrollments.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The registration overview. The list of terms may be empty.")
    @ApiResponse(responseCode = "404", description = "The account has no student profile.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RegistrationOverview overview(@AuthenticationPrincipal Jwt token) {
        return service.overview(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/course-registration")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('COURSE_REGISTER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register for course offerings",
            description = "Registers the student for every chosen offering or for none. Each must be open for "
                    + "registration, in a semester the student is enrolled in, inside its registration period, for the "
                    + "student's section, not taken before, with a free place, and within the program's credit limit.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new course enrollments.")
    @ApiResponse(responseCode = "400", description = "No offering, more than 20, or one chosen twice. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No student profile, or a chosen offering does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A chosen offering cannot be taken; the message says which and "
            + "why. Nothing is saved.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RegistrationResult register(@AuthenticationPrincipal Jwt token, @Valid @RequestBody RegistrationRequest request) {
        return service.register(AuthenticatedAccount.idFrom(token), request.courseOfferingIds());
    }

    @PostMapping("/course-enrollments/{courseEnrollmentId}/drop")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('COURSE_DROP')")
    @Operation(summary = "Drop a course",
            description = "Drops one of the student's own enrolled courses before the semester's drop deadline. The "
                    + "enrollment is kept as DROPPED with the time, and the same offering cannot be taken again.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The dropped course.")
    @ApiResponse(responseCode = "404", description = "No such course enrollment of this student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "It is not enrolled, dropping is not allowed for its semester, "
            + "or the deadline has passed.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RegisteredCourse drop(@AuthenticationPrincipal Jwt token, @PathVariable Long courseEnrollmentId) {
        return service.drop(AuthenticatedAccount.idFrom(token), courseEnrollmentId);
    }
}
