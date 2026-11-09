package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptService;
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

/**
 * Students take the published quizzes of courses they may use now. The student comes from the access token and the
 * course enrollment from official enrollment; questions are sent without anything that gives their answers away.
 */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS quizzes")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course, quiz, or attempt is not available to this student. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsStudentQuizController {

    private static final String TAKE = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_QUIZ_TAKE')";

    private final LmsQuizAttemptService quizzes;

    public LmsStudentQuizController(LmsQuizAttemptService quizzes) {
        this.quizzes = quizzes;
    }

    @GetMapping("/courses/{lmsCourseId}/quizzes")
    @PreAuthorize(TAKE)
    @Operation(summary = "List a course's published quizzes", description = "Newest first, each with the student's own attempts.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quizzes. The list may be empty.")
    public List<LmsStudentQuizResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return quizzes.list(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(LmsStudentQuizResponse::of).toList();
    }

    @GetMapping("/courses/{lmsCourseId}/quizzes/{quizId}")
    @PreAuthorize(TAKE)
    @Operation(summary = "Read a published quiz", description = "Its rules and the student's own attempts, never its questions.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    public LmsStudentQuizResponse quiz(@PathVariable Long lmsCourseId, @PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        return LmsStudentQuizResponse.of(quizzes.quiz(AuthenticatedAccount.idFrom(token), lmsCourseId, quizId));
    }

    @PostMapping("/courses/{lmsCourseId}/quizzes/{quizId}/attempts")
    @PreAuthorize(TAKE)
    @Operation(summary = "Start or resume an attempt",
            description = "Returns the attempt still open, or starts a new one while the quiz is open and attempts are left. "
                    + "The deadline is fixed at the start from the time limit and the closing time.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt with its questions.")
    @ApiResponse(responseCode = "409", description = "The quiz is not open yet or has closed, or no attempts are left.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizAttemptResponse start(@PathVariable Long lmsCourseId, @PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        Long userAccountId = AuthenticatedAccount.idFrom(token);
        Long attemptId = quizzes.start(userAccountId, lmsCourseId, quizId).getId();
        return LmsQuizAttemptResponse.of(quizzes.attempt(userAccountId, attemptId));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    @PreAuthorize(TAKE)
    @Operation(summary = "Read one of the student's own attempts", description = "Its questions in the attempt's order and the answers saved so far.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt.")
    public LmsQuizAttemptResponse attempt(@PathVariable Long attemptId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAttemptResponse.of(quizzes.attempt(AuthenticatedAccount.idFrom(token), attemptId));
    }

    @PutMapping("/quiz-attempts/{attemptId}/answers/{quizQuestionId}")
    @PreAuthorize(TAKE)
    @Operation(summary = "Save the answer to one question", description = "Replaces an earlier answer while the attempt is open.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The saved answer.")
    @ApiResponse(responseCode = "400", description = "The answer does not fit the question.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Time is up, or the attempt was handed in.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizAnswerResponse saveAnswer(@PathVariable Long attemptId, @PathVariable Long quizQuestionId,
            @Valid @RequestBody LmsQuizAnswerRequest request, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAnswerResponse.of(quizzes.saveAnswer(AuthenticatedAccount.idFrom(token), attemptId, quizQuestionId, request.input()));
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    @PreAuthorize(TAKE)
    @Operation(summary = "Hand in an attempt",
            description = "Submitted before the deadline; after it, closed as expired at the deadline. Handing in again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The attempt.")
    public LmsQuizAttemptResponse submit(@PathVariable Long attemptId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizAttemptResponse.of(quizzes.submit(AuthenticatedAccount.idFrom(token), attemptId));
    }
}
