package com.stacc.backend.lms.ai.api;

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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The student AI study assistant of one LMS course. Each request checks again that the signed-in student may open the
 * course, takes the student from the access token, and saves nothing. Answers come only from the course's published
 * content and cite it.
 */
@RestController
@RequestMapping("/api/lms/courses/{lmsCourseId}/ai")
@Tag(name = "LMS AI study assistant")
public class AiStudyController {

    private final AiStudyService study;

    public AiStudyController(AiStudyService study) {
        this.study = study;
    }

    @PostMapping("/ask")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(
            summary = "Ask a question about an accessible course",
            description = "Answers only from the course's published topics and study materials, with citations. When "
                    + "they do not support an answer, insufficientContext is true and there is no answer. Nothing is "
                    + "saved, and nothing about the student is sent to the AI service.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @StudentAiResponses
    public AiAnswerResponse ask(@PathVariable Long lmsCourseId, @Valid @RequestBody AiAskRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return study.ask(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request);
    }

    @PostMapping("/topics/{topicId}/summary")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(
            summary = "Summarise a published topic",
            description = "A short summary with key points of a topic the student can see in this course, from its "
                    + "text and study materials, with citations. Draft, archived, and other courses' topics answer 404.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @StudentAiResponses
    public AiTopicSummaryResponse summary(@PathVariable Long lmsCourseId, @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt accessToken) {
        return study.summarize(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, topicId);
    }

    @PostMapping("/practice")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(
            summary = "Generate practice questions",
            description = "1 to 10 questions (5 by default) with suggested answers and citations, for the course or "
                    + "one published topic. For practice only: nothing is saved or graded, and no assignment or "
                    + "submission is created.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @StudentAiResponses
    public AiPracticeResponse practice(@PathVariable Long lmsCourseId, @Valid @RequestBody AiPracticeRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return study.practice(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request);
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "The result, or insufficientContext when the sources do not support one.")
    @ApiResponse(responseCode = "400", description = "The request is not valid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The course or topic does not exist or is not available to this student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "AI study content is not ready for this course yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "The AI service gave an unusable result.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI learning features are switched off or temporarily unavailable.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface StudentAiResponses {
    }
}
