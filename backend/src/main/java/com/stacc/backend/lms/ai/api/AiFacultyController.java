package com.stacc.backend.lms.ai.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.job.AiJobService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Faculty AI tools for a course the signed-in faculty member teaches, checked with the faculty access policy on every
 * request. A course they may not use answers the same 404 as a missing one.
 */
@RestController
@RequestMapping("/api/v1/lms/faculty/courses/{lmsCourseId}/ai")
@Tag(name = "LMS AI teaching tools")
public class AiFacultyController {

    private static final String ERROR = "application/json";

    private final AiFacultyService faculty;
    private final AiQuestionBankDraftService bankDrafts;

    public AiFacultyController(AiFacultyService faculty, AiQuestionBankDraftService bankDrafts) {
        this.faculty = faculty;
        this.bankDrafts = bankDrafts;
    }

    @PostMapping("/question-bank")
    @PreAuthorize(AiAccess.FACULTY_BANK)
    @Operation(
            summary = "Draft questions into the question bank",
            description = "AI drafts questions of the chosen types from the course's published content and current teacher "
                    + "documents. Choice and true/false questions whose key the AI does not reproduce are dropped. Every "
                    + "question left that cites a real course source is added to the bank as NEEDS_REVIEW, marked as drafted "
                    + "by AI, with its sources. Nothing is offered for practice, put in a quiz, or published.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "How many questions were added, dropped, or left out.")
    @ApiResponse(responseCode = "400", description = "A setting is missing or out of range.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course or topic does not exist or is not taught by this faculty member.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI knowledge is not ready for the current content.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Another AI request of this account is running, or AI is busy.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI is switched off or unavailable.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiQuestionBankDraftResponse questionBank(@PathVariable Long lmsCourseId,
            @Valid @RequestBody AiQuestionBankDraftRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return bankDrafts.draft(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request);
    }

    @GetMapping("/status")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(
            summary = "Get the course's AI knowledge status",
            description = "NOT_INDEXED, BUILDING, READY, or FAILED for the latest refresh, whether published content "
                    + "changed since the last successful one (stale), and how many published sources there are. It "
                    + "works while AI is switched off and then says enabled=false.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The status.")
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course does not exist or is not taught by this faculty member.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiKnowledgeStatusResponse status(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return faculty.status(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PutMapping("/student-access")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(
            summary = "Turn the student AI Study Assistant on or off",
            description = "While it is off, students' questions, summaries, and practice in this course answer 409. AI Tools "
                    + "and students' notes keep working. Setting the current value changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The new state.")
    @ApiResponse(responseCode = "400", description = "enabled is missing.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course does not exist or is not taught by this faculty member.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiStudentAccessResponse changeStudentAccess(@PathVariable Long lmsCourseId,
            @Valid @RequestBody AiStudentAccessRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return new AiStudentAccessResponse(faculty.changeStudentAi(AuthenticatedAccount.idFrom(accessToken), lmsCourseId,
                request.enabled()));
    }

    @PostMapping("/jobs")
    @PreAuthorize(AiAccess.FACULTY)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Queue a refresh of the course's AI knowledge",
            description = "Answers at once with the queued job; a background worker rebuilds the knowledge from the course's "
                    + "published topics and study materials. Follow it with the status route while it is queued or running.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "No published content can be indexed, or a refresh is already queued or running.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI learning features are switched off.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiJobService.JobView queue(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return faculty.queueRefresh(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/sync")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(
            deprecated = true,
            summary = "Refresh the course's AI knowledge in the request (replaced by the jobs route)",
            description = "Rebuilds the AI knowledge from the course's published topics and study materials only, "
                    + "replacing the previous index once the new one is complete. Unreadable or unsupported files are "
                    + "skipped and counted. It may take a while for large courses.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The knowledge is ready.")
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course does not exist or is not taught by this faculty member.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "No published content can be indexed, or a refresh is already running.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "The AI service gave an unusable result.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI learning features are switched off or temporarily unavailable.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiKnowledgeSyncResponse sync(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return faculty.sync(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/assessment-draft")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(
            summary = "Generate an assessment draft for review",
            description = "Drafts questions with suggested answers, marks, and citations from the course's published "
                    + "content. The draft is not saved and creates, publishes, or grades nothing: the teacher reviews "
                    + "and edits it, and only the normal assignment form creates an assignment.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The draft, or insufficientContext when the sources do not support one.")
    @ApiResponse(responseCode = "400", description = "The request is not valid.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course does not exist or is not taught by this faculty member.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The course's AI knowledge is not ready or is out of date.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "The AI service gave an unusable result.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI learning features are switched off or temporarily unavailable.",
            content = @Content(mediaType = ERROR, schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiAssessmentDraftResponse assessmentDraft(@PathVariable Long lmsCourseId,
            @Valid @RequestBody AiAssessmentDraftRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return faculty.assessmentDraft(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request);
    }
}
