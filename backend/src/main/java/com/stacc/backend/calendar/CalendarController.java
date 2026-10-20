package com.stacc.backend.calendar;

import java.time.LocalDate;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account's own calendar. The route needs a student, faculty, or admin role and
 * {@code ACADEMIC_CALENDAR_VIEW}; what it shows is still decided by the account's own enrollments, teaching
 * assignments, and LMS access, taken from the access token. No account, profile, or enrollment ID is accepted.
 */
@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar")
public class CalendarController {

    private final AcademicCalendarQueryService calendar;

    public CalendarController(AcademicCalendarQueryService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('ACADEMIC_CALENDAR_VIEW')")
    @Operation(
            summary = "Get the signed-in account's calendar",
            description = "Returns, for a range of at most 366 days, the official academic events that apply to the account and the assignment deadlines of each role it holds. A student sees events of their own enrolled or completed semesters and their sessions, and the published assignments of the LMS courses they may use now. A faculty member sees events of the semesters they actively teach and their sessions, with no LMS course needed, and the draft and published assignments of the LMS courses they teach and can open. An admin sees every published event. Draft events never appear; an event cancelled after it was published appears as CANCELLED. An account with several roles gets every part. Nothing is stored.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The calendar. Each list may be empty.")
    @ApiResponse(
            responseCode = "400",
            description = "A date or the time zone is missing or invalid, the from date is after the to date, or the range is longer than 366 days.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MyCalendarResponse myCalendar(
            @AuthenticationPrincipal Jwt accessToken,
            Authentication authentication,
            @Parameter(description = "The first day, such as 2026-10-01.", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "The last day, included, such as 2026-10-31.", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "The IANA time zone the days are read in, such as Asia/Kolkata. It only decides "
                    + "which deadlines fall on those days.", required = true)
            @RequestParam String timeZone) {
        CalendarViewer viewer = CalendarViewer.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
        return calendar.getMyCalendar(viewer, from, to, timeZone);
    }
}
