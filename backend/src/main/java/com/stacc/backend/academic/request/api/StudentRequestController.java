package com.stacc.backend.academic.request.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.academic.request.StudentRequest;
import com.stacc.backend.academic.request.StudentRequestService;
import com.stacc.backend.academic.request.StudentRequestService.RequestView;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Student requests to the ERP office, and the office's queue. */
@RestController
@Tag(name = "ERP requests")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such request, record, or result for the signed-in account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "409", description = "Already open, already decided, or not allowed in its state.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class StudentRequestController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('ERP_REQUEST_SUBMIT')";
    private static final String OFFICE = "hasRole('ADMIN') and hasAuthority('ERP_REQUEST_MANAGE')";

    private final StudentRequestService service;

    public StudentRequestController(StudentRequestService service) {
        this.service = service;
    }

    public record LeaveRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, @NotNull @Size(max = 1000) String reason) {
    }

    public record CorrectionRequest(@NotNull Long attendanceRecordId, @NotNull @Size(max = 1000) String reason) {
    }

    public record RevaluationRequest(@NotNull Long courseResultId, @NotNull @Size(max = 1000) String reason) {
    }

    public record DecisionRequest(@Size(max = 1000) String comment, BigDecimal newMarks) {
    }

    @GetMapping("/api/v1/erp/requests")
    @PreAuthorize(STUDENT)
    @Operation(summary = "The signed-in student's requests", description = "Newest first, with their status and outcome.")
    public List<RequestView> mine(@AuthenticationPrincipal Jwt accessToken) {
        return service.mine(AuthenticatedAccount.idFrom(accessToken));
    }

    @PostMapping("/api/v1/erp/requests/medical-leave")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for medical leave", description = "At most 30 days, within 60 days of today.")
    public RequestView medicalLeave(@AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody LeaveRequest request) {
        return service.medicalLeave(AuthenticatedAccount.idFrom(accessToken), request.fromDate(), request.toDate(), request.reason());
    }

    @PostMapping("/api/v1/erp/requests/attendance-correction")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for an absence to be corrected", description = "Only one's own finalized absences; one open request each.")
    public RequestView correction(@AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody CorrectionRequest request) {
        return service.correction(AuthenticatedAccount.idFrom(accessToken), request.attendanceRecordId(), request.reason());
    }

    @PostMapping("/api/v1/erp/requests/revaluation")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for a result to be checked again", description = "Only one's own published results; one open request each.")
    public RequestView revaluation(@AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody RevaluationRequest request) {
        return service.revaluation(AuthenticatedAccount.idFrom(accessToken), request.courseResultId(), request.reason());
    }

    @PostMapping("/api/v1/erp/requests/{requestId}/cancel")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Cancel one's own request before it is decided")
    public RequestView cancel(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long requestId) {
        return service.cancel(AuthenticatedAccount.idFrom(accessToken), requestId);
    }

    @GetMapping("/api/v1/admin/erp/requests")
    @PreAuthorize(OFFICE)
    @Operation(summary = "The office's queue", description = "Submitted first, then newest; up to 200, filtered by kind, status, and college ID.")
    public List<RequestView> queue(@RequestParam(required = false) StudentRequest.Kind kind,
            @RequestParam(required = false) StudentRequest.Status status, @RequestParam(required = false) String q) {
        return service.queue(kind, status, q);
    }

    @PostMapping("/api/v1/admin/erp/requests/{requestId}/approve")
    @PreAuthorize(OFFICE)
    @Operation(summary = "Approve a request", description = "A correction marks the class present; a revaluation applies newMarks when given.")
    public RequestView approve(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long requestId,
            @Valid @RequestBody DecisionRequest request) {
        return service.approve(AuthenticatedAccount.idFrom(accessToken), requestId, request.comment(), request.newMarks());
    }

    @PostMapping("/api/v1/admin/erp/requests/{requestId}/reject")
    @PreAuthorize(OFFICE)
    @Operation(summary = "Reject a request", description = "A comment the student can read is required.")
    public RequestView reject(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long requestId,
            @Valid @RequestBody DecisionRequest request) {
        return service.reject(AuthenticatedAccount.idFrom(accessToken), requestId, request.comment());
    }
}
