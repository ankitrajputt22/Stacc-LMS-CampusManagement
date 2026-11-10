package com.stacc.backend.grievance.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.grievance.GrievanceAdminService;
import com.stacc.backend.grievance.GrievanceAdminService.CategoryView;
import com.stacc.backend.grievance.GrievanceCase;
import com.stacc.backend.grievance.GrievanceService;
import com.stacc.backend.grievance.GrievanceService.CaseSummary;
import com.stacc.backend.grievance.GrievanceService.CaseView;
import com.stacc.backend.grievance.GrievanceService.CategoryOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grievances: complainants submit, follow, appeal, and accept their own cases; committees work the cases of their
 * categories; admins manage categories and committees but never read cases. A case the caller may not open is a 404.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Grievances")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such case, category, or member that the account may open.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class GrievanceController {

    private static final String SUBMIT = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('GRIEVANCE_SUBMIT')";
    private static final String COMMITTEE = "hasAnyRole('FACULTY', 'ADMIN') and hasAuthority('GRIEVANCE_COMMITTEE_WORK')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('GRIEVANCE_MANAGE')";

    private final GrievanceService service;
    private final GrievanceAdminService admin;

    public GrievanceController(GrievanceService service, GrievanceAdminService admin) {
        this.service = service;
        this.admin = admin;
    }

    public record SubmitRequest(@NotNull Long categoryId, @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = GrievanceCase.MAX_TEXT) String details) {
    }

    public record TextRequest(@NotBlank @Size(max = GrievanceCase.MAX_TEXT) String text) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name,
            @Min(1) @Max(90) int responseDays) {
    }

    public record CategoryChangeRequest(@NotBlank @Size(max = 150) String name, @Min(1) @Max(90) int responseDays, boolean active) {
    }

    public record MemberRequest(@NotBlank @Size(max = 100) String loginId) {
    }

    // --- Complainants

    @GetMapping("/grievances/categories")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "Open grievance categories", description = "With each committee's response time in days.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CategoryOption> categories() {
        return service.openCategories();
    }

    @PostMapping("/grievances")
    @PreAuthorize(SUBMIT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a grievance", description = "Confidential: only its committee and the complainant open it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView submit(@Valid @RequestBody SubmitRequest request, @AuthenticationPrincipal Jwt token) {
        return service.submit(AuthenticatedAccount.idFrom(token), request.categoryId(), request.subject(), request.details());
    }

    @GetMapping("/grievances/me")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "My grievances", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CaseSummary> mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/grievances/me/{caseId}")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "One of my grievances", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView myCase(@PathVariable Long caseId, @AuthenticationPrincipal Jwt token) {
        return service.myCase(AuthenticatedAccount.idFrom(token), caseId);
    }

    @PostMapping("/grievances/me/{caseId}/appeal")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "Appeal the resolution", description = "Once, within 14 days of the resolution.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not resolved, already appealed, or too late.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CaseView appeal(@PathVariable Long caseId, @Valid @RequestBody TextRequest request, @AuthenticationPrincipal Jwt token) {
        return service.appeal(AuthenticatedAccount.idFrom(token), caseId, request.text());
    }

    @PostMapping("/grievances/me/{caseId}/accept")
    @PreAuthorize(SUBMIT)
    @Operation(summary = "Accept the resolution", description = "Closes the case.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView accept(@PathVariable Long caseId, @AuthenticationPrincipal Jwt token) {
        return service.accept(AuthenticatedAccount.idFrom(token), caseId);
    }

    // --- Committees

    @GetMapping("/grievances/committee")
    @PreAuthorize(COMMITTEE)
    @Operation(summary = "Cases of my committees", description = "Never one's own complaints.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CaseSummary> committeeCases(@AuthenticationPrincipal Jwt token) {
        return service.committeeCases(AuthenticatedAccount.idFrom(token));
    }

    @GetMapping("/grievances/committee/{caseId}")
    @PreAuthorize(COMMITTEE)
    @Operation(summary = "A case of my committees", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView committeeCase(@PathVariable Long caseId, @AuthenticationPrincipal Jwt token) {
        return service.committeeCase(AuthenticatedAccount.idFrom(token), caseId);
    }

    @PostMapping("/grievances/committee/{caseId}/review")
    @PreAuthorize(COMMITTEE)
    @Operation(summary = "Take a case under review", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView startReview(@PathVariable Long caseId, @AuthenticationPrincipal Jwt token) {
        return service.startReview(AuthenticatedAccount.idFrom(token), caseId);
    }

    @PostMapping("/grievances/committee/{caseId}/resolve")
    @PreAuthorize(COMMITTEE)
    @Operation(summary = "Resolve a case", description = "With the committee's resolution in plain text.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Already resolved or closed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CaseView resolve(@PathVariable Long caseId, @Valid @RequestBody TextRequest request, @AuthenticationPrincipal Jwt token) {
        return service.resolve(AuthenticatedAccount.idFrom(token), caseId, request.text());
    }

    @PostMapping("/grievances/committee/{caseId}/appeal-decision")
    @PreAuthorize(COMMITTEE)
    @Operation(summary = "Decide an appeal", description = "Closes the case.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CaseView decideAppeal(@PathVariable Long caseId, @Valid @RequestBody TextRequest request, @AuthenticationPrincipal Jwt token) {
        return service.decideAppeal(AuthenticatedAccount.idFrom(token), caseId, request.text());
    }

    // --- Admins

    @GetMapping("/admin/grievances/categories")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Grievance categories with their committees", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CategoryView> adminCategories() {
        return admin.categories();
    }

    @PostMapping("/admin/grievances/categories")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a grievance category", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest request) {
        return admin.create(request.code(), request.name(), request.responseDays());
    }

    @PutMapping("/admin/grievances/categories/{categoryId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a grievance category", description = "Name, response time, and whether it takes new cases; the code is fixed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CategoryView changeCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryChangeRequest request) {
        return admin.change(categoryId, request.name(), request.responseDays(), request.active());
    }

    @PostMapping("/admin/grievances/categories/{categoryId}/committee")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Add a committee member", description = "An active faculty or admin account; audited.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Not an eligible account, or already on the committee.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CategoryView addMember(@PathVariable Long categoryId, @Valid @RequestBody MemberRequest request, @AuthenticationPrincipal Jwt token) {
        return admin.addMember(AuthenticatedAccount.idFrom(token), categoryId, request.loginId());
    }

    @PostMapping("/admin/grievances/committee/{memberId}/end")
    @PreAuthorize(MANAGE)
    @Operation(summary = "End a committee membership", description = "Audited; the record is kept.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CategoryView endMember(@PathVariable Long memberId, @AuthenticationPrincipal Jwt token) {
        return admin.endMember(AuthenticatedAccount.idFrom(token), memberId);
    }
}
