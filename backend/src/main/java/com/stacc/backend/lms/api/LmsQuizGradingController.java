package com.stacc.backend.lms.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.quiz.attempt.LmsQuizGradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quiz results and marking, for the faculty who teach the course. */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS quizzes")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The quiz or attempt is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsQuizGradingController {

    private static final String GRADE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_QUIZ_GRADE')";

    private final LmsQuizGradingService grading;

    public LmsQuizGradingController(LmsQuizGradingService grading) {
        this.grading = grading;
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    @PreAuthorize(GRADE)
    @Operation(summary = "Read a quiz's results",
            description = "Every attempt with automatic marks, teachers' marks, and the score once fully marked. Published and archived quizzes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The results.")
    public LmsQuizResultsResponse results(@PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizResultsResponse.of(grading.results(AuthenticatedAccount.idFrom(token), quizId));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    @PreAuthorize(GRADE)
    @Operation(summary = "Review an attempt", description = "Every question with its answers, the student's answer, and the marks.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt.")
    public LmsQuizAttemptReviewResponse review(@PathVariable Long attemptId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAttemptReviewResponse.of(grading.review(AuthenticatedAccount.idFrom(token), attemptId));
    }

    @PutMapping("/quiz-attempts/{attemptId}/answers/{quizQuestionId}/mark")
    @PreAuthorize(GRADE)
    @Operation(summary = "Mark an answer",
            description = "Marks from 0 to the question's marks, with optional feedback, replacing an earlier mark. For an automatically marked answer this is a regrade.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt.")
    @ApiResponse(responseCode = "400", description = "The marks are out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The attempt is not handed in, or the question is unanswered.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizAttemptReviewResponse mark(@PathVariable Long attemptId, @PathVariable Long quizQuestionId,
            @Valid @RequestBody LmsQuizMarkRequest request, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAttemptReviewResponse.of(grading.mark(AuthenticatedAccount.idFrom(token), attemptId, quizQuestionId,
                request.marks(), request.feedback()));
    }

    @PostMapping("/quiz-attempts/{attemptId}/close")
    @PreAuthorize(GRADE)
    @Operation(summary = "Close an attempt whose time is up",
            description = "Hands in an attempt the student left open after its deadline, so it can be marked. Closing again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt.")
    @ApiResponse(responseCode = "409", description = "The attempt's time is not up.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizAttemptReviewResponse close(@PathVariable Long attemptId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAttemptReviewResponse.of(grading.close(AuthenticatedAccount.idFrom(token), attemptId));
    }
}
