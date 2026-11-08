package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.outcome.LmsOutcomeService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Course learning outcomes: students read the active ones; faculty add, correct, and archive them. */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS course outcomes")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or outcome is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsOutcomeController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')";
    private static final String FACULTY_VIEW = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')";
    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsOutcomeService outcomes;

    public LmsOutcomeController(LmsOutcomeService outcomes) {
        this.outcomes = outcomes;
    }

    @GetMapping("/courses/{lmsCourseId}/outcomes")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Read the outcomes of an accessible course", description = "The active outcomes, by code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The outcomes. The list may be empty.")
    public List<LmsOutcomeResponse> forStudent(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return outcomes.forStudent(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(LmsOutcomeResponse::of).toList();
    }

    @GetMapping("/faculty/courses/{lmsCourseId}/outcomes")
    @PreAuthorize(FACULTY_VIEW)
    @Operation(summary = "Read the outcomes of a course the faculty member teaches", description = "Every outcome, archived ones included, by code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The outcomes. The list may be empty.")
    public List<LmsOutcomeResponse> forFaculty(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return outcomes.forFaculty(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(LmsOutcomeResponse::of).toList();
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/outcomes")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an outcome", description = "The server gives it the course's next code (CO1, CO2, …), never reused.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new outcome.")
    @ApiResponse(responseCode = "400", description = "The description is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsOutcomeResponse add(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsOutcomeRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsOutcomeResponse.of(outcomes.add(AuthenticatedAccount.idFrom(token), lmsCourseId, request.description()));
    }

    @PutMapping("/faculty/outcomes/{outcomeId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Correct an outcome's description", description = "The code never changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The outcome.")
    @ApiResponse(responseCode = "409", description = "The outcome is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsOutcomeResponse describe(@PathVariable Long outcomeId, @Valid @RequestBody LmsOutcomeRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsOutcomeResponse.of(outcomes.describe(AuthenticatedAccount.idFrom(token), outcomeId, request.description()));
    }

    @PostMapping("/faculty/outcomes/{outcomeId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive an outcome",
            description = "It stays on what it was aligned to but takes no new alignments and is hidden from students. Nothing restores or deletes it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The outcome.")
    public LmsOutcomeResponse archive(@PathVariable Long outcomeId, @AuthenticationPrincipal Jwt token) {
        return LmsOutcomeResponse.of(outcomes.archive(AuthenticatedAccount.idFrom(token), outcomeId));
    }
}
