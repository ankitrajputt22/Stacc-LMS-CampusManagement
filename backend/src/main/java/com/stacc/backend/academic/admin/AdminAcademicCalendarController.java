package com.stacc.backend.academic.admin;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventCreate;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventUpdate;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management of the official academic calendar. Reading needs the admin role and {@code ACADEMIC_CALENDAR_VIEW};
 * creating, editing, publishing, and cancelling also need {@code ACADEMIC_CALENDAR_MANAGE}. Events are never deleted
 * or restored, and assignment deadlines are not managed here: they belong to LMS assignments.
 */
@RestController
@RequestMapping("/api/admin/academic/calendar/events")
@Tag(name = "Admin academic calendar")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminAcademicCalendarController {

    private final AcademicCalendarManagementService calendar;

    public AdminAcademicCalendarController(AcademicCalendarManagementService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    @PreAuthorize(AdminAcademicAccess.CALENDAR_VIEW)
    @Operation(
            summary = "List academic calendar events",
            description = "Returns events in every status, DRAFT, PUBLISHED, and CANCELLED, with their session and semester: the earliest start first. Each filter that is sent narrows the list. The dates keep events that overlap them, including events running across either edge; a semester keeps the events of exactly that semester.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The events. The list may be empty.")
    @ApiResponse(
            responseCode = "400",
            description = "A filter is not valid, such as a date that is not YYYY-MM-DD or a from date after the to date.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<AdminCalendarEventResponse> list(
            @Parameter(description = "Only events that end on or after this date, such as 2026-10-01.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Only events that start on or before this date, such as 2026-10-31.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only events of this academic session.") @RequestParam(required = false) Long academicSessionId,
            @Parameter(description = "Only events of this semester.") @RequestParam(required = false) Long semesterId,
            @Parameter(description = "Only events in this status.") @RequestParam(required = false) AcademicCalendarEventStatus status) {
        return calendar.list(from, to, academicSessionId, semesterId, status);
    }

    @GetMapping("/{eventId}")
    @PreAuthorize(AdminAcademicAccess.CALENDAR_VIEW)
    @Operation(summary = "Get one academic calendar event", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The event.")
    @ApiResponse(
            responseCode = "404",
            description = "The event does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCalendarEventResponse get(@PathVariable Long eventId) {
        return calendar.get(eventId);
    }

    @PostMapping
    @PreAuthorize(AdminAcademicAccess.CALENDAR_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create an academic calendar event",
            description = "Adds a DRAFT event to an academic session, optionally for one semester of that same session. No student or faculty member sees it until it is published. The status cannot be sent.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new draft event.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, the end date is before the start date, or the semester is not part of the session. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The selected academic session or semester does not exist. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCalendarEventResponse create(@Valid @RequestBody AcademicCalendarEventCreate request) {
        return calendar.create(request);
    }

    @PutMapping("/{eventId}")
    @PreAuthorize(AdminAcademicAccess.CALENDAR_MANAGE)
    @Operation(
            summary = "Edit an academic calendar event",
            description = "Replaces the title, description, category, and dates. A DRAFT event may also move to another session or semester. Once published, the session and semester are fixed and only corrections are allowed; the event stays published and keeps its publication time. A CANCELLED event cannot be edited.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed event.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, the end date is before the start date, or the semester is not part of the session. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The event, or the selected session or semester of a draft, does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The event is cancelled, or it was published and a different session or semester was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCalendarEventResponse update(@PathVariable Long eventId, @Valid @RequestBody AcademicCalendarEventUpdate request) {
        return calendar.update(eventId, request);
    }

    @PostMapping("/{eventId}/publish")
    @PreAuthorize(AdminAcademicAccess.CALENDAR_MANAGE)
    @Operation(
            summary = "Publish an academic calendar event",
            description = "Puts a DRAFT event on the calendar of the students and faculty it applies to, and records the time. Publishing an already published event changes nothing. Nothing is sent to anyone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published event.")
    @ApiResponse(
            responseCode = "404",
            description = "The event does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The event is cancelled and cannot be published again.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCalendarEventResponse publish(@PathVariable Long eventId) {
        return calendar.publish(eventId);
    }

    @PostMapping("/{eventId}/cancel")
    @PreAuthorize(AdminAcademicAccess.CALENDAR_MANAGE)
    @Operation(
            summary = "Cancel an academic calendar event",
            description = "Calls off a DRAFT or PUBLISHED event for good and records the time. An event that was published stays on the calendar, marked as cancelled; a draft never appears. Cancelling an already cancelled event changes nothing. Nothing is deleted or sent to anyone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The cancelled event.")
    @ApiResponse(
            responseCode = "404",
            description = "The event does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCalendarEventResponse cancel(@PathVariable Long eventId) {
        return calendar.cancel(eventId);
    }
}
