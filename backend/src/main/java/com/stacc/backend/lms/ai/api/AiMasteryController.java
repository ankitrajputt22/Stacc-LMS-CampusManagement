package com.stacc.backend.lms.ai.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.mastery.AiMasteryService;
import com.stacc.backend.lms.ai.mastery.AiRecommendationService;
import com.stacc.backend.lms.ai.mastery.Mastery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in student's own mastery of a course's published pages (v0.15.5). Nobody else has a route to it. */
@RestController
@RequestMapping("/api/v1/lms/courses/{lmsCourseId}/ai/mastery")
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course is missing or not open to this student.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiMasteryController {

    public record TopicMasteryResponse(
            long topicId,
            String title,
            @Schema(description = "NOT_STARTED, LEARNING, PRACTISING, or MASTERED.") Mastery.Level level,
            @Schema(description = "From 0 to 1, recent evidence weighted more.") double mastery,
            @Schema(description = "From 0 to 1; grows with the amount of evidence.") double confidence,
            int evidence,
            @Schema(nullable = true) Instant lastPractisedAt) {
    }

    private final AiMasteryService mastery;
    private final AiRecommendationService recommendations;

    public AiMasteryController(AiMasteryService mastery, AiRecommendationService recommendations) {
        this.mastery = mastery;
        this.recommendations = recommendations;
    }

    @GetMapping("/recommendations")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "What to do next",
            description = "Up to six suggestions from fixed rules, each with its reason: the weakest pages, flashcards due "
                    + "today, an assignment due within a week, pages not started, and mastered pages not practised for three "
                    + "weeks (v0.15.6). Worked out on read from the student's own records; no AI is used.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The suggestions.")
    public List<AiRecommendationService.Recommendation> recommendations(@PathVariable Long lmsCourseId,
            @AuthenticationPrincipal Jwt accessToken) {
        return recommendations.recommend(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @GetMapping
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "My mastery of the course",
            description = "Every published page in course order with the student's mastery, confidence, and level, worked out "
                    + "from their practice answers and flashcard reviews. Needs no AI.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The pages.")
    public List<TopicMasteryResponse> view(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return mastery.view(AuthenticatedAccount.idFrom(accessToken), lmsCourseId).stream()
                .map(topic -> new TopicMasteryResponse(topic.topicId(), topic.title(), topic.result().level(), topic.result().mastery(),
                        topic.result().confidence(), topic.result().evidence(), topic.result().lastPractisedAt()))
                .toList();
    }

    @DeleteMapping
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear my learning evidence in the course",
            description = "Removes the student's evidence in this course for good; their mastery starts over.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Cleared.")
    public void clear(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        mastery.clear(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }
}
