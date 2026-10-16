package com.stacc.backend.lms.api;

import java.nio.charset.StandardCharsets;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own assignment submissions: hand in an attempt, read back their attempts, and download their
 * own files. The student is always the account in the access token, and their course enrollment is found
 * on the server; no route takes an account, student, or enrollment ID. There is no faculty access here yet.
 */
@RestController
@RequestMapping("/api/lms")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsAssignmentSubmissionController {

    // Only lets the caller ask. Which assignments and files they get is decided by the service, from
    // official enrollment, on every request.
    private static final String SUBMIT =
            "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_SUBMIT')";

    private final LmsAssignmentSubmissionService submissionService;

    public LmsAssignmentSubmissionController(LmsAssignmentSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping(value = "/courses/{lmsCourseId}/assignments/{assignmentId}/submissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SUBMIT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Hand in an attempt for an assignment",
            description = "Adds a new attempt for the signed-in student. Send multipart/form-data with textContent, files, "
                    + "or both: at least one of them is required. Earlier attempts are kept unchanged. Late work is accepted "
                    + "and marked late against the current deadline, which is stored with the attempt.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new attempt.")
    @ApiResponse(responseCode = "400", description = "Neither text nor a file was sent, the text is too long, there are too "
            + "many files, or a file is empty, too large, of a type that is not allowed, or not what its name says.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The assignment does not exist, is not published, belongs to another "
            + "course, or the course is not available to this student. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "The upload is larger than the server accepts.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "File storage is not available right now. Nothing was saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsSubmissionAttemptResponse submit(
            @PathVariable Long lmsCourseId,
            @PathVariable Long assignmentId,
            @Valid @ModelAttribute LmsSubmissionRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsSubmissionAttemptResponse.of(submissionService.submit(
                AuthenticatedAccount.idFrom(accessToken), lmsCourseId, assignmentId, request.textContent(), request.files()));
    }

    @GetMapping("/courses/{lmsCourseId}/assignments/{assignmentId}/submissions/me")
    @PreAuthorize(SUBMIT)
    @Operation(
            summary = "Get the signed-in student's own attempts for an assignment",
            description = "Returns the student's attempts, newest first, each with its text, files, and whether it was late. "
                    + "A student who has not handed anything in gets an empty list.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempts. The list may be empty.")
    @ApiResponse(responseCode = "404", description = "The assignment does not exist, is not published, belongs to another "
            + "course, or the course is not available to this student. All look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsSubmissionHistoryResponse mySubmissions(
            @PathVariable Long lmsCourseId, @PathVariable Long assignmentId, @AuthenticationPrincipal Jwt accessToken) {
        return new LmsSubmissionHistoryResponse(submissionService
                .history(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, assignmentId)
                .stream()
                .map(LmsSubmissionAttemptResponse::of)
                .toList());
    }

    @GetMapping("/submission-attachments/{attachmentId}/download")
    @PreAuthorize(SUBMIT)
    @Operation(
            summary = "Download one of the signed-in student's own submission files",
            description = "Sends the file as an attachment, with its stored name, type, and size. Only the student who "
                    + "handed it in may download it, while they may use the course and the assignment is published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The file, as an attachment.",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "The file does not exist or is not this student's. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "File storage is not available right now.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<InputStreamResource> download(@PathVariable Long attachmentId, @AuthenticationPrincipal Jwt accessToken) {
        LmsAssignmentSubmissionService.Download download =
                submissionService.openOwnAttachment(AuthenticatedAccount.idFrom(accessToken), attachmentId);
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
}
