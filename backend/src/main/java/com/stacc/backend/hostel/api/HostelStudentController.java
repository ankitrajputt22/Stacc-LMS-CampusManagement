package com.stacc.backend.hostel.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.hostel.HostelApplicationService;
import com.stacc.backend.hostel.HostelQueryService;
import com.stacc.backend.hostel.HostelRequests.CreateHostelApplicationRequest;
import com.stacc.backend.hostel.HostelResponses.EligibleHostelSession;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationDetail;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own hostel applications. The student always comes from the access token; no route takes a student,
 * profile, or account ID. Students never choose a hostel, room, or bed, and never decide approvals.
 */
@RestController
@RequestMapping("/api/hostel/student")
@Tag(name = "Hostel")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelStudentController {

    private static final String NOT_FOUND = "The application does not exist, or belongs to another student.";

    private final HostelApplicationService applications;
    private final HostelQueryService queries;

    public HostelStudentController(HostelApplicationService applications, HostelQueryService queries) {
        this.applications = applications;
        this.queries = queries;
    }

    @GetMapping("/eligible-sessions")
    @PreAuthorize(HostelAccess.APPLY)
    @Operation(
            summary = "List my hostel sessions",
            description = "Returns the academic sessions the signed-in student may apply for: those in which they have an ENROLLED semester enrollment, the latest first, each marked when a pending or approved application already exists. Empty without an active student profile.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The sessions. The list may be empty.")
    public List<EligibleHostelSession> eligibleSessions(@AuthenticationPrincipal Jwt accessToken) {
        return applications.eligibleSessions(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/applications")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @Operation(summary = "List my hostel applications",
            description = "Returns the signed-in student's applications, newest first, each with its approval status from the approval engine.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The applications. The list may be empty.")
    public List<HostelApplicationSummary> mine(@AuthenticationPrincipal Jwt accessToken) {
        return queries.studentApplications(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @Operation(summary = "Get one of my hostel applications",
            description = "Returns the application with its approval steps, its active allocation, and any earlier allocations.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The application.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelApplicationDetail application(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long applicationId) {
        return queries.studentApplication(AuthenticatedAccount.idFrom(accessToken), applicationId);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HostelAccess.APPLY)
    @Operation(
            summary = "Apply for hostel",
            description = "Applies for a hostel place in one academic session the student is enrolled in, with an optional note, and starts its approval with the HOSTEL_APPLICATION workflow. The application gets a number such as HST-000001.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new application, with approval pending.")
    @ApiResponse(responseCode = "400", description = "The session is missing or the note is too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The academic session does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The student is not enrolled in the session, already has a pending or approved application for it, or applications are closed.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelApplicationDetail apply(@AuthenticationPrincipal Jwt accessToken,
            @Valid @RequestBody CreateHostelApplicationRequest request) {
        return applications.apply(AuthenticatedAccount.idFrom(accessToken), request);
    }

    @PostMapping("/applications/{applicationId}/cancel")
    @PreAuthorize(HostelAccess.CANCEL)
    @Operation(
            summary = "Cancel my hostel application",
            description = "Cancels the student's own application while its approval is pending, through the approval engine. Cancelling an already cancelled application changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The application, now cancelled.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The application is approved or rejected, or has an active allocation.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HostelApplicationDetail cancel(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long applicationId) {
        return applications.cancel(AuthenticatedAccount.idFrom(accessToken), applicationId);
    }
}
