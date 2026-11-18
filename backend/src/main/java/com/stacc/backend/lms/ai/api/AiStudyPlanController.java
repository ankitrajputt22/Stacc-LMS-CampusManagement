package com.stacc.backend.lms.ai.api;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.planner.AiStudyPlanService;
import com.stacc.backend.lms.ai.planner.StudyPlanner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms/courses/{lmsCourseId}/ai/study-plan")
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "400", description = "The days or minutes are not allowed.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course is missing or not open to this student.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiStudyPlanController {

    public record StudyPlanRequest(@Schema(description = "7 or 14.") @NotNull @Min(7) @Max(14) Integer days,
            @Schema(description = "From 30 to 240.") @NotNull @Min(30) @Max(240) Integer minutesPerDay) {
    }

    public record StudySessionResponse(StudyPlanner.Kind kind, Long targetId, String title, int minutes) {
    }

    public record StudyDayResponse(LocalDate date, boolean busy, int budget, int planned, List<StudySessionResponse> sessions) {
    }

    public record StudyPlanAddedResponse(int added) {
    }

    private final AiStudyPlanService plans;

    public AiStudyPlanController(AiStudyPlanService plans) {
        this.plans = plans;
    }

    @PostMapping
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "Plan my study",
            description = "A plan for the next 7 or 14 days, scheduled by code: assignment work before its deadline, then weak "
                    + "pages, pages not started, pages to refresh, and flashcards, within the minutes a day, halved on days your "
                    + "own calendar is busy. Nothing is stored.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The plan, one entry per day.")
    public List<StudyDayResponse> plan(@PathVariable Long lmsCourseId, @Valid @RequestBody StudyPlanRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return plans.plan(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.days(), request.minutesPerDay()).stream()
                .map(day -> new StudyDayResponse(day.date(), day.busy(), day.budget(), day.planned(), day.sessions().stream()
                        .map(session -> new StudySessionResponse(session.kind(), session.targetId(), session.title(), session.minutes()))
                        .toList()))
                .toList();
    }

    @PostMapping("/calendar")
    @PreAuthorize(AiAccess.STUDENT + " and hasAuthority('CALENDAR_PERSONAL_USE')")
    @Operation(summary = "Add the plan to my calendar",
            description = "Works the plan out again and adds one personal calendar entry per day with sessions.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "How many entries were added.")
    public StudyPlanAddedResponse addToCalendar(@PathVariable Long lmsCourseId, @Valid @RequestBody StudyPlanRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return new StudyPlanAddedResponse(plans.addToCalendar(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.days(),
                request.minutesPerDay()));
    }

    @PostMapping("/todo")
    @PreAuthorize(AiAccess.STUDENT + " and hasAuthority('TODO_VIEW')")
    @Operation(summary = "Add the plan to my To-Do list",
            description = "Works the plan out again and saves its sessions; each shows on the To-Do list from its day until "
                    + "dismissed. Sessions already added are skipped.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "How many sessions were added.")
    public StudyPlanAddedResponse addToTodo(@PathVariable Long lmsCourseId, @Valid @RequestBody StudyPlanRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return new StudyPlanAddedResponse(plans.addToTodo(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.days(),
                request.minutesPerDay()));
    }
}
