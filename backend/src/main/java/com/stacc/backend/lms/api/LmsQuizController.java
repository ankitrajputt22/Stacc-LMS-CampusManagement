package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.quiz.LmsQuizService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The quizzes of a course, for the faculty who teach it: settings, questions from the bank, publishing, archiving. */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS quizzes")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or quiz is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsQuizController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_QUIZ_MANAGE')";

    private final LmsQuizService quizzes;

    public LmsQuizController(LmsQuizService quizzes) {
        this.quizzes = quizzes;
    }

    @GetMapping("/courses/{lmsCourseId}/quizzes")
    @PreAuthorize(MANAGE)
    @Operation(summary = "List a course's quizzes", description = "Every quiz, archived ones included, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quizzes. The list may be empty.")
    public List<LmsQuizSummaryResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return quizzes.list(AuthenticatedAccount.idFrom(token), lmsCourseId).stream().map(LmsQuizSummaryResponse::of).toList();
    }

    @PostMapping("/courses/{lmsCourseId}/quizzes")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a quiz", description = "The quiz starts as a draft without questions.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new quiz.")
    @ApiResponse(responseCode = "400", description = "A setting is missing or out of range, or the times are out of order.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse create(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsQuizRequest request,
            @AuthenticationPrincipal Jwt token) {
        Long userAccountId = AuthenticatedAccount.idFrom(token);
        Long quizId = quizzes.create(userAccountId, lmsCourseId, request.settings()).getId();
        return LmsQuizResponse.of(quizzes.detail(userAccountId, quizId));
    }

    @GetMapping("/quizzes/{quizId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Read a quiz", description = "Its settings and questions in order.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    public LmsQuizResponse detail(@PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.detail(AuthenticatedAccount.idFrom(token), quizId));
    }

    @PutMapping("/quizzes/{quizId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a quiz's settings",
            description = "After publishing, the type, shuffling, and negative marking stay as they are.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "400", description = "A setting is missing or out of range, or the times are out of order.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The quiz is archived, or a setting fixed by publishing would change.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse update(@PathVariable Long quizId, @Valid @RequestBody LmsQuizRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.update(AuthenticatedAccount.idFrom(token), quizId, request.settings()));
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Add a bank question to a draft quiz", description = "It goes at the end, with the bank's marks unless others are given.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "409", description = "The quiz is not a draft, the question is not an active question of this course, it is already in the quiz, or the quiz is full.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse addQuestion(@PathVariable Long quizId, @Valid @RequestBody LmsQuizQuestionAddRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.addQuestion(AuthenticatedAccount.idFrom(token), quizId, request.questionId(), request.marks()));
    }

    @PutMapping("/quizzes/{quizId}/questions/{quizQuestionId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a question's marks in a draft quiz", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "409", description = "The quiz is not a draft.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse changeMarks(@PathVariable Long quizId, @PathVariable Long quizQuestionId,
            @Valid @RequestBody LmsQuizQuestionMarksRequest request, @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.changeMarks(AuthenticatedAccount.idFrom(token), quizId, quizQuestionId, request.marks()));
    }

    @DeleteMapping("/quizzes/{quizId}/questions/{quizQuestionId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Take a question out of a draft quiz",
            description = "Only a draft quiz's question list changes; the question stays in the bank, and published quizzes never change.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "409", description = "The quiz is not a draft.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse removeQuestion(@PathVariable Long quizId, @PathVariable Long quizQuestionId,
            @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.removeQuestion(AuthenticatedAccount.idFrom(token), quizId, quizQuestionId));
    }

    @PutMapping("/quizzes/{quizId}/question-order")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Reorder a draft quiz's questions", description = "The list names every question of the quiz exactly once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "400", description = "The list leaves out, repeats, or adds a question.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The quiz is not a draft.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse reorder(@PathVariable Long quizId, @Valid @RequestBody LmsQuizQuestionOrderRequest request,
            @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.reorder(AuthenticatedAccount.idFrom(token), quizId, request.quizQuestionIds()));
    }

    @PostMapping("/quizzes/{quizId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a quiz",
            description = "Needs at least one question, each active and reviewed (and offered for practice in a practice quiz). Publishing again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    @ApiResponse(responseCode = "409", description = "The quiz is archived, has no questions, or a question is not ready.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuizResponse publish(@PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.publish(AuthenticatedAccount.idFrom(token), quizId));
    }

    @PostMapping("/quizzes/{quizId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a quiz", description = "It stays stored and readable but can no longer change. Nothing deletes or restores it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The quiz.")
    public LmsQuizResponse archive(@PathVariable Long quizId, @AuthenticationPrincipal Jwt token) {
        return LmsQuizResponse.of(quizzes.archive(AuthenticatedAccount.idFrom(token), quizId));
    }
}
