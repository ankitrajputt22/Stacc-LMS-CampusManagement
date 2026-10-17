package com.stacc.backend.lms.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Faculty review and grade students' submissions. The faculty member is taken from the access token, and
 * whether they may review a course is decided from official teaching assignments on every request; no route
 * takes a faculty, profile, or student ID. A grade is given to one attempt.
 */
@RestController
@RequestMapping("/api/lms/faculty")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "404",
        description = "It does not exist, its assignment is not published, or the course is not one this account "
                + "teaches now. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsFacultySubmissionController {

    // Only lets the caller ask. Which courses they may review is decided by the services, from official
    // teaching assignments, on every request.
    private static final String GRADE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_GRADE')";

    private final LmsFacultySubmissionQueryService queryService;
    private final LmsAssignmentGradingService gradingService;

    public LmsFacultySubmissionController(
            LmsFacultySubmissionQueryService queryService, LmsAssignmentGradingService gradingService) {
        this.queryService = queryService;
        this.gradingService = gradingService;
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    @PreAuthorize(GRADE)
    @Operation(
            summary = "Get the students' submissions for an assignment",
            description = "Returns one row per student who has handed in work, summed up by their latest attempt: its "
                    + "number, time, whether it was late, and its grade if any. Newest latest attempt first, then by "
                    + "submission. Students who have not handed anything in are not listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The submissions. The list may be empty.")
    public List<LmsFacultySubmissionSummaryResponse> submissions(
            @PathVariable Long assignmentId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.listSubmissions(AuthenticatedAccount.idFrom(accessToken), assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    @PreAuthorize(GRADE)
    @Operation(
            summary = "Get one student's submission for review",
            description = "Returns the student's login ID and every attempt, newest first, with its text, files, the "
                    + "deadline that applied, whether it was late, and its grade if any.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The submission.")
    public LmsFacultySubmissionDetailResponse submission(
            @PathVariable Long assignmentId, @PathVariable Long submissionId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.submissionDetail(AuthenticatedAccount.idFrom(accessToken), assignmentId, submissionId);
    }

    @GetMapping("/submission-attachments/{attachmentId}/download")
    @PreAuthorize(GRADE)
    @Operation(
            summary = "Download a student's submitted file for review",
            description = "Sends the file as an attachment, with its stored name, type, and size, to a faculty member "
                    + "who may review its assignment.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The file, as an attachment.",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "503", description = "File storage is not available right now.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<InputStreamResource> download(@PathVariable Long attachmentId, @AuthenticationPrincipal Jwt accessToken) {
        LmsAssignmentSubmissionService.Download download =
                queryService.openAttachment(AuthenticatedAccount.idFrom(accessToken), attachmentId);
        // Always an attachment, never shown inline, so an uploaded file cannot act as a page of this site.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(download.content()));
    }

    @PutMapping("/submission-attempts/{attemptId}/grade")
    @PreAuthorize(GRADE)
    @Operation(
            summary = "Grade an attempt, or revise its grade",
            description = "Saves the marks and optional feedback of one attempt; the student sees them at once. A first "
                    + "grade is out of the assignment's maximum now, which stays with the grade. A revision keeps that "
                    + "maximum and records the latest grader and time. Lateness never changes the marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt's grade.")
    @ApiResponse(responseCode = "400", description = "The marks are missing, negative, above the maximum, or have more "
            + "than two decimals, or the feedback is too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsSubmissionGradeResponse grade(
            @PathVariable Long attemptId,
            @Valid @RequestBody LmsGradeSubmissionAttemptRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsSubmissionGradeResponse.of(gradingService.grade(
                AuthenticatedAccount.idFrom(accessToken), attemptId, request.marksAwarded(), request.feedback()));
    }
}
