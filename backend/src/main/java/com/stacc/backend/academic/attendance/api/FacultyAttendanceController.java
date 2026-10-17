package com.stacc.backend.academic.attendance.api;

import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceManagementService;
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

/**
 * Official attendance as faculty take it. Every route needs the faculty role and the attendance permission;
 * which course offerings the faculty member may use is decided from official teaching assignments on every
 * request, with no LMS course involved. The faculty member is taken from the access token. There is no delete
 * and no reopen.
 */
@RestController
@RequestMapping("/api/attendance/faculty")
@Tag(name = "Attendance")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FacultyAttendanceController {

    private static final String MANAGE = "hasRole('FACULTY') and hasAuthority('ATTENDANCE_MANAGE')";

    private final FacultyAttendanceQueryService queryService;
    private final AttendanceManagementService managementService;

    public FacultyAttendanceController(
            FacultyAttendanceQueryService queryService, AttendanceManagementService managementService) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping("/my-courses")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Get the course offerings the faculty member takes attendance for",
            description = "Returns the course offerings the signed-in faculty member is actively assigned to teach, in "
                    + "course code order, whether or not they have an LMS course. The request has no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course offerings. The list may be empty.")
    public List<AttendanceCourseOfferingResponse> myCourses(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getMyCourseOfferings(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/course-offerings/{courseOfferingId}/sessions")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Get the attendance sessions of a course offering",
            description = "Returns the open and finalized sessions, the latest class first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The sessions. The list may be empty.")
    @ApiResponse(
            responseCode = "404",
            description = "The course offering does not exist or the faculty member does not teach it. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<AttendanceSessionResponse> sessions(
            @PathVariable Long courseOfferingId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getSessions(AuthenticatedAccount.idFrom(accessToken), courseOfferingId);
    }

    @PostMapping("/course-offerings/{courseOfferingId}/sessions")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create an attendance session",
            description = "Creates an open session and puts every student currently enrolled in the course offering on "
                    + "its roster, unmarked. The roster comes from official course enrollments, never from the request, "
                    + "and later enrollment changes do not alter it. Students do not see the session until it is "
                    + "finalized.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new session.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid, such as a time without a time zone.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The course offering does not exist or the faculty member does not teach it. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AttendanceSessionResponse create(
            @PathVariable Long courseOfferingId,
            @Valid @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return AttendanceSessionResponse.of(managementService.createSession(
                AuthenticatedAccount.idFrom(accessToken), courseOfferingId, request.heldAt(), request.topic()));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Get an attendance session with its roster",
            description = "Returns the session and every student on its roster, in student ID order, each with their "
                    + "college ID, status, and when it was last marked.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The session.")
    @ApiResponse(
            responseCode = "404",
            description = "The session does not exist or the faculty member does not teach its course offering. Both "
                    + "look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AttendanceSessionDetailResponse session(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt accessToken) {
        return queryService.getSessionDetail(AuthenticatedAccount.idFrom(accessToken), sessionId);
    }

    @PutMapping("/sessions/{sessionId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Change an open session's date and topic",
            description = "Changes when the class was held and its topic. The roster is not changed. A finalized "
                    + "session can no longer be changed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed session.")
    @ApiResponse(
            responseCode = "400",
            description = "A field is missing or invalid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The session does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The session is finalized.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AttendanceSessionResponse update(
            @PathVariable Long sessionId,
            @Valid @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return AttendanceSessionResponse.of(managementService.updateSession(
                AuthenticatedAccount.idFrom(accessToken), sessionId, request.heldAt(), request.topic()));
    }

    @PutMapping("/sessions/{sessionId}/records")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Save attendance for a session",
            description = "Saves the given students' statuses, by the attendanceRecordId of each roster row. While the "
                    + "session is open, any status may be saved, so attendance can be saved part of the way. Once it is "
                    + "finalized, a row can only be corrected between PRESENT and ABSENT, and students see the correction "
                    + "at once. Each change records the faculty member and the time. Returns the whole session.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The session with its roster after saving.")
    @ApiResponse(
            responseCode = "400",
            description = "A record is listed twice or does not belong to this session, a field is missing, or a "
                    + "finalized row would become UNMARKED. Nothing is saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The session does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AttendanceSessionDetailResponse saveRecords(
            @PathVariable Long sessionId,
            @Valid @RequestBody AttendanceRecordsRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        Long accountId = AuthenticatedAccount.idFrom(accessToken);
        managementService.saveRecords(accountId, sessionId, request.records().stream()
                .map(change -> new AttendanceManagementService.RecordChange(change.attendanceRecordId(), change.status()))
                .toList());
        return queryService.getSessionDetail(accountId, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/finalize")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Finalize an attendance session",
            description = "Finalizes an open session once every student on its roster is PRESENT or ABSENT. Students "
                    + "then see their own record. Finalizing a finalized session changes nothing. There is no reopen.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The finalized session.")
    @ApiResponse(
            responseCode = "400",
            description = "A student is still unmarked.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "The session does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AttendanceSessionResponse finalizeSession(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt accessToken) {
        return AttendanceSessionResponse.of(managementService.finalizeSession(AuthenticatedAccount.idFrom(accessToken), sessionId));
    }
}
