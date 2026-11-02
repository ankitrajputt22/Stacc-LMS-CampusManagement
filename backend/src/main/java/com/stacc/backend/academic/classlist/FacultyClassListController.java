package com.stacc.backend.academic.classlist;

import java.util.List;

import com.stacc.backend.academic.classlist.FacultyClassListService.ClassList;
import com.stacc.backend.academic.classlist.FacultyClassListService.TaughtOffering;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in faculty member's class lists, read-only. The faculty member always comes from the access token. */
@RestController
@RequestMapping("/api/v1/faculty/me/course-offerings")
@Tag(name = "Class lists")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not faculty or lacks the permission.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FacultyClassListController {

    private static final String ACCESS = "hasRole('FACULTY') and hasAuthority('ENROLLED_STUDENTS_VIEW')";

    private final FacultyClassListService service;

    public FacultyClassListController(FacultyClassListService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ACCESS)
    @Operation(summary = "Offerings I teach",
            description = "The course offerings the faculty member teaches now through an active teaching assignment, "
                    + "with how many students are enrolled. An LMS course is not needed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The offerings; the list may be empty.")
    public List<TaughtOffering> myOfferings(@AuthenticationPrincipal Jwt token) {
        return service.myOfferings(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/{courseOfferingId}/students")
    @PreAuthorize(ACCESS)
    @Operation(summary = "Students of an offering I teach",
            description = "Every course enrollment of the offering, whatever its status, by student ID: student ID, "
                    + "name, roll number, program, semester, section, and status. Never contact details.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The class list.")
    @ApiResponse(responseCode = "404", description = "The offering does not exist or the faculty member does not teach it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ClassList students(@AuthenticationPrincipal Jwt token, @PathVariable Long courseOfferingId) {
        return service.classList(AuthenticatedAccount.idFrom(token), courseOfferingId);
    }
}
