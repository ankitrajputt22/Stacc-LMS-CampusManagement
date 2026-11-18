package com.stacc.backend.fees.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.fees.FeeAdjustment;
import com.stacc.backend.fees.FeePayment;
import com.stacc.backend.fees.FeeService;
import com.stacc.backend.fees.FeeService.AdjustmentView;
import com.stacc.backend.fees.FeeService.Item;
import com.stacc.backend.fees.FeeService.Ledger;
import com.stacc.backend.fees.FeeService.PaymentView;
import com.stacc.backend.fees.FeeService.Structure;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fees")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such student, program, session, or request.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class FeeController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('FEE_VIEW_OWN')";
    private static final String OFFICE = "hasRole('ADMIN') and hasAuthority('FEE_MANAGE')";

    private final FeeService fees;

    public FeeController(FeeService fees) {
        this.fees = fees;
    }

    public record ScholarshipRequest(@NotNull @Size(max = 150) String title, @NotNull BigDecimal amount, @NotNull @Size(max = 1000) String reason) {
    }

    public record RefundRequest(@NotNull BigDecimal amount, @NotNull @Size(max = 1000) String reason) {
    }

    public record PaymentRequest(@NotNull BigDecimal amount, @NotNull FeePayment.Mode mode, @Size(max = 60) String reference,
            @NotNull LocalDate paidOn) {
    }

    public record DecisionRequest(boolean approve, @Size(max = 1000) String comment) {
    }

    public record StructureRequest(@NotNull LocalDate dueDate, @NotNull @Size(max = 7) List<@NotNull @Valid Item> items) {
    }

    @GetMapping("/api/v1/erp/fees/me")
    @PreAuthorize(STUDENT)
    @Operation(summary = "The signed-in student's fees", description = "Fee heads, payments, scholarships, refunds, and what is due, worked out on read.")
    public Ledger mine(@AuthenticationPrincipal Jwt accessToken) {
        return fees.mine(AuthenticatedAccount.idFrom(accessToken));
    }

    @PostMapping("/api/v1/erp/fees/me/scholarships")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for a scholarship or fee waiver")
    public AdjustmentView applyForScholarship(@AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody ScholarshipRequest request) {
        return fees.applyForScholarship(AuthenticatedAccount.idFrom(accessToken), request.title(), request.amount(), request.reason());
    }

    @PostMapping("/api/v1/erp/fees/me/refunds")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask for a refund", description = "At most what was paid beyond the fees (409 otherwise).")
    public AdjustmentView askForRefund(@AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody RefundRequest request) {
        return fees.askForRefund(AuthenticatedAccount.idFrom(accessToken), request.amount(), request.reason());
    }

    @GetMapping("/api/v1/admin/erp/fees/students/{loginId}")
    @PreAuthorize(OFFICE)
    @Operation(summary = "A student's fee ledger")
    public Ledger ledger(@PathVariable String loginId) {
        return fees.ledgerFor(loginId);
    }

    @PostMapping("/api/v1/admin/erp/fees/students/{loginId}/payments")
    @PreAuthorize(OFFICE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment", description = "Recorded once with a receipt number; never changed or deleted.")
    public PaymentView recordPayment(@AuthenticationPrincipal Jwt accessToken, @PathVariable String loginId,
            @Valid @RequestBody PaymentRequest request) {
        return fees.recordPayment(AuthenticatedAccount.idFrom(accessToken), loginId, request.amount(), request.mode(), request.reference(),
                request.paidOn());
    }

    @GetMapping("/api/v1/admin/erp/fees/requests")
    @PreAuthorize(OFFICE)
    @Operation(summary = "Scholarship and refund requests", description = "Submitted first, then newest; up to 200.")
    public List<AdjustmentView> queue(@RequestParam(required = false) FeeAdjustment.Status status) {
        return fees.queue(status);
    }

    @PostMapping("/api/v1/admin/erp/fees/requests/{adjustmentId}/decide")
    @PreAuthorize(OFFICE)
    @Operation(summary = "Approve or reject a scholarship or refund", description = "Once; a rejection needs a comment.")
    public AdjustmentView decide(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long adjustmentId,
            @Valid @RequestBody DecisionRequest request) {
        return fees.decide(AuthenticatedAccount.idFrom(accessToken), adjustmentId, request.approve(), request.comment());
    }

    @GetMapping("/api/v1/admin/erp/fees/structures")
    @PreAuthorize(OFFICE)
    @Operation(summary = "A program's fees for a session")
    public Structure structure(@RequestParam Long programId, @RequestParam Long academicSessionId) {
        return fees.structure(programId, academicSessionId);
    }

    @PutMapping("/api/v1/admin/erp/fees/structures")
    @PreAuthorize(OFFICE)
    @Operation(summary = "Set a program's fees for a session", description = "Each listed fee head is added or changed; the due date is set.")
    public Structure setStructure(@RequestParam Long programId, @RequestParam Long academicSessionId,
            @Valid @RequestBody StructureRequest request) {
        return fees.setStructure(programId, academicSessionId, request.dueDate(), request.items());
    }
}
