package com.stacc.backend.lms.api;

import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
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

/** The question bank of a course, for the faculty who teach it. Students have no route here: questions carry answers. */
@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS question bank")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or question is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsQuestionBankController {

    private static final String MANAGE =
            "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_QUESTION_BANK_MANAGE')";

    private final LmsQuestionBankService bank;

    public LmsQuestionBankController(LmsQuestionBankService bank) {
        this.bank = bank;
    }

    @GetMapping("/courses/{lmsCourseId}/questions")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Read a course's question bank", description = "Every question with its answers, archived ones included, newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The questions. The list may be empty.")
    public List<LmsQuestionResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        Long userAccountId = AuthenticatedAccount.idFrom(token);
        List<LmsQuestion> questions = bank.list(userAccountId, lmsCourseId);
        Set<Long> inPublishedQuizzes = bank.inPublishedQuizzes(userAccountId, lmsCourseId);
        return questions.stream()
                .map(question -> LmsQuestionResponse.of(question, inPublishedQuizzes.contains(question.getId())))
                .toList();
    }

    @PostMapping("/courses/{lmsCourseId}/questions")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a question", description = "The question starts active and needing review, not offered for practice.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new question.")
    @ApiResponse(responseCode = "400", description = "The question or its answers do not fit its type.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The outcome is not an active outcome of this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuestionResponse add(@PathVariable Long lmsCourseId, @Valid @RequestBody LmsQuestionRequest request,
            @AuthenticationPrincipal Jwt token) {
        return response(bank.add(AuthenticatedAccount.idFrom(token), lmsCourseId, request.content(), request.outcomeId()));
    }

    @PutMapping("/questions/{questionId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Edit a question", description = "Replaces the question and its answers. It then needs a new review and is no longer offered for practice.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The question.")
    @ApiResponse(responseCode = "400", description = "The question or its answers do not fit its type.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The question is archived or in a published quiz, or the outcome is not an active outcome of this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuestionResponse edit(@PathVariable Long questionId, @Valid @RequestBody LmsQuestionRequest request,
            @AuthenticationPrincipal Jwt token) {
        return response(bank.edit(AuthenticatedAccount.idFrom(token), questionId, request.content(), request.outcomeId()));
    }

    @PostMapping("/questions/{questionId}/review")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Mark a question reviewed", description = "Reviewing again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The question.")
    @ApiResponse(responseCode = "409", description = "The question is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuestionResponse review(@PathVariable Long questionId, @AuthenticationPrincipal Jwt token) {
        return response(bank.markReviewed(AuthenticatedAccount.idFrom(token), questionId));
    }

    @PutMapping("/questions/{questionId}/practice")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Offer a question for practice, or stop offering it", description = "Only a reviewed question can be offered.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The question.")
    @ApiResponse(responseCode = "409", description = "The question is archived or not reviewed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsQuestionResponse practice(@PathVariable Long questionId, @Valid @RequestBody LmsQuestionPracticeRequest request,
            @AuthenticationPrincipal Jwt token) {
        return response(bank.allowPractice(AuthenticatedAccount.idFrom(token), questionId, request.allowed()));
    }

    @PostMapping("/questions/{questionId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Archive a question", description = "It stays stored and readable but can no longer change. Nothing deletes or restores it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The question.")
    public LmsQuestionResponse archive(@PathVariable Long questionId, @AuthenticationPrincipal Jwt token) {
        return response(bank.archive(AuthenticatedAccount.idFrom(token), questionId));
    }

    private LmsQuestionResponse response(LmsQuestion question) {
        return LmsQuestionResponse.of(question, bank.isInPublishedQuiz(question.getId()));
    }
}
