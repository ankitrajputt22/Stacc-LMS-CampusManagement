package com.stacc.backend.lms.ai.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.ai.control.AiCourseControlService;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.ai.mastery.AiMasteryService;
import com.stacc.backend.lms.ai.mastery.AiRecommendationService;
import com.stacc.backend.lms.ai.mastery.Mastery;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiStudyHomeController {

    public record CourseStudy(long lmsCourseId, String courseCode, String courseName, Map<Mastery.Level, Integer> pagesByLevel,
            long flashcardsDue, @Schema(nullable = true) AiRecommendationService.Recommendation nextStep,
            @Schema(description = "False when the course's teachers have turned the student assistant off.") boolean assistantOn) {
    }

    private final LmsStudentCourseQueryService courses;
    private final AiMasteryService mastery;
    private final AiRecommendationService recommendations;
    private final AiFlashcardStore flashcards;
    private final AiCourseControlService controls;
    private final Clock clock;
    private final EngagementProperties college;

    public AiStudyHomeController(LmsStudentCourseQueryService courses, AiMasteryService mastery, AiRecommendationService recommendations,
            AiFlashcardStore flashcards, AiCourseControlService controls, Clock clock, EngagementProperties college) {
        this.controls = controls;
        this.courses = courses;
        this.mastery = mastery;
        this.recommendations = recommendations;
        this.flashcards = flashcards;
        this.clock = clock;
        this.college = college;
    }

    @GetMapping("/api/v1/lms/ai/study-home")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "My AI study home",
            description = "For every course the student may open now: how many published pages are at each mastery level, "
                    + "flashcards due today, and the first recommendation with its reason. Read only; no AI.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One entry per course.")
    public List<CourseStudy> home(@AuthenticationPrincipal Jwt accessToken) {
        Long accountId = AuthenticatedAccount.idFrom(accessToken);
        LocalDate today = clock.instant().atZone(college.zone()).toLocalDate();
        List<CourseStudy> result = new ArrayList<>();
        for (LmsCourseSummaryResponse course : courses.getAccessibleCourses(accountId)) {
            Map<Mastery.Level, Integer> levels = new EnumMap<>(Mastery.Level.class);
            for (Mastery.Level level : Mastery.Level.values()) {
                levels.put(level, 0);
            }
            List<AiMasteryService.TopicMastery> topics = mastery.view(accountId, course.lmsCourseId());
            topics.forEach(topic -> levels.merge(topic.result().level(), 1, Integer::sum));
            List<AiFlashcardStore.SetSummary> sets = flashcards.list(accountId, course.lmsCourseId(), today);
            long due = sets.stream().mapToLong(AiFlashcardStore.SetSummary::due).sum();
            List<AiRecommendationService.Recommendation> next = recommendations.recommend(accountId, course.lmsCourseId(), topics, sets);
            result.add(new CourseStudy(course.lmsCourseId(), course.courseCode(), course.courseName(), levels, due,
                    next.isEmpty() ? null : next.get(0), controls.studentAiEnabled(course.lmsCourseId())));
        }
        return List.copyOf(result);
    }
}
