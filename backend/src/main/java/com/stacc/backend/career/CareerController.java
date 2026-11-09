package com.stacc.backend.career;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.career.CareerService.ApplicationView;
import com.stacc.backend.career.CareerService.OpportunityInput;
import com.stacc.backend.career.CareerService.OpportunityView;
import com.stacc.backend.career.CareerService.ProfileView;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.report.ReportCsv;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** A student's career profile and applications, and the placement office's opportunities, applicants, and offers. */
@RestController
@RequestMapping("/api/v1/career")
@Tag(name = "Career and placement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CareerController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('CAREER_USE')";
    private static final String MANAGER = "hasAnyRole('ADMIN', 'PLACEMENT_OFFICER') and hasAuthority('CAREER_MANAGE')";

    public record ProfileRequest(@Size(max = 200) String headline, @Size(max = 1000) String skills, @Size(max = 1500) String links) {
    }

    public record ApplyRequest(Long resumeId, @Size(max = 2000) String coverNote, boolean shareConsent) {
    }

    public record AnswerRequest(boolean accept) {
    }

    public record CompanyRequest(@NotBlank @Size(max = 200) String name, @Size(max = 300) String website, @Size(max = 2000) String description) {
    }

    public record OpportunityRequest(@NotNull CareerOpportunity.Kind kind, @NotBlank @Size(max = 200) String title, Long companyId,
            @Size(max = 200) String organization, @Size(max = 10_000) String description, @Size(max = 50) List<Long> programIds,
            @NotNull Instant deadline, @NotNull @Size(min = 1, max = 10) List<String> stages) {

        OpportunityInput input() {
            return new OpportunityInput(kind, title, companyId, organization, description, programIds, deadline, stages);
        }
    }

    public record RoundRequest(int stageIndex, @NotNull Instant startsAt, @Size(max = 200) String venue) {
    }

    public record MoveRequest(boolean advance) {
    }

    public record OfferRequest(@NotBlank @Size(max = 200) String roleTitle, @Size(max = 200) String compensation, Instant respondBy) {
    }

    public record PolicyBody(boolean oneAcceptedPlacement) {
    }

    public record CompanyView(Long companyId, String name, String website, String description, boolean active) {

        static CompanyView of(CareerCompany company) {
            return new CompanyView(company.getId(), company.getName(), company.getWebsite(), company.getDescription(), company.isActive());
        }
    }

    private final CareerService career;

    public CareerController(CareerService career) {
        this.career = career;
    }

    @GetMapping("/profile")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Your career profile and resumes", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProfileView profile(@AuthenticationPrincipal Jwt token) {
        return career.profile(AuthenticatedAccount.idFrom(token));
    }

    @PutMapping("/profile")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Change your headline, skills, and links", description = "Skills and links one per line; links start with https://.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProfileView editProfile(@AuthenticationPrincipal Jwt token, @Valid @RequestBody ProfileRequest request) {
        return career.editProfile(AuthenticatedAccount.idFrom(token), request.headline(), request.skills(), request.links());
    }

    @PostMapping(value = "/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a resume version", description = "multipart/form-data with label and file. Versions are never changed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProfileView addResume(@AuthenticationPrincipal Jwt token, @RequestParam String label,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return career.addResume(AuthenticatedAccount.idFrom(token), label, file);
    }

    @PostMapping("/resumes/{resumeId}/archive")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Archive a resume version", description = "Applications keep it.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ProfileView archiveResume(@AuthenticationPrincipal Jwt token, @PathVariable Long resumeId) {
        return career.archiveResume(AuthenticatedAccount.idFrom(token), resumeId);
    }

    @GetMapping("/resumes/{resumeId}/download")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Download one of your resumes", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<InputStreamResource> downloadResume(@AuthenticationPrincipal Jwt token, @PathVariable Long resumeId) {
        LmsAttachmentStore.Download file = career.openOwnResume(AuthenticatedAccount.idFrom(token), resumeId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.content()));
    }

    @GetMapping("/opportunities")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Open opportunities", description = "With why you may not apply, if so.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OpportunityView> open(@AuthenticationPrincipal Jwt token) {
        return career.open(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/opportunities/{opportunityId}/applications")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply", description = "With one of your resume versions; sharing your details with the recruiter needs your consent.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ApplicationView apply(@AuthenticationPrincipal Jwt token, @PathVariable Long opportunityId, @Valid @RequestBody ApplyRequest request) {
        return career.apply(AuthenticatedAccount.idFrom(token), opportunityId, request.resumeId(), request.coverNote(), request.shareConsent());
    }

    @GetMapping("/applications/mine")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Your applications and offers", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ApplicationView> mine(@AuthenticationPrincipal Jwt token) {
        return career.myApplications(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Withdraw your application", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ApplicationView withdraw(@AuthenticationPrincipal Jwt token, @PathVariable Long applicationId) {
        return career.withdraw(AuthenticatedAccount.idFrom(token), applicationId);
    }

    @PostMapping("/applications/{applicationId}/offer/answer")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Accept or decline your offer", description = "Once; the placement policy may allow only one accepted placement.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ApplicationView answer(@AuthenticationPrincipal Jwt token, @PathVariable Long applicationId, @RequestBody AnswerRequest request) {
        return career.answerOffer(AuthenticatedAccount.idFrom(token), applicationId, request.accept());
    }

    @GetMapping("/manage/companies")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Companies", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CompanyView> companies() {
        return career.companies().stream().map(CompanyView::of).toList();
    }

    @PostMapping("/manage/companies")
    @PreAuthorize(MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a company", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public CompanyView addCompany(@Valid @RequestBody CompanyRequest request) {
        return CompanyView.of(career.addCompany(request.name(), request.website(), request.description()));
    }

    @GetMapping("/manage/opportunities")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Every opportunity", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<OpportunityView> managed() {
        return career.managed();
    }

    @PostMapping("/manage/opportunities")
    @PreAuthorize(MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft opportunity", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OpportunityView create(@AuthenticationPrincipal Jwt token, @Valid @RequestBody OpportunityRequest request) {
        return career.createOpportunity(AuthenticatedAccount.idFrom(token), request.input());
    }

    @PutMapping("/manage/opportunities/{opportunityId}")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Edit a draft opportunity", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OpportunityView edit(@PathVariable Long opportunityId, @Valid @RequestBody OpportunityRequest request) {
        return career.editOpportunity(opportunityId, request.input());
    }

    @PostMapping("/manage/opportunities/{opportunityId}/open")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Open a draft for applications", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OpportunityView openOne(@PathVariable Long opportunityId) {
        return career.setStatus(opportunityId, true);
    }

    @PostMapping("/manage/opportunities/{opportunityId}/close")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Close an opportunity", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OpportunityView close(@PathVariable Long opportunityId) {
        return career.setStatus(opportunityId, false);
    }

    @PutMapping("/manage/opportunities/{opportunityId}/rounds")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Schedule a stage's round", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public OpportunityView round(@PathVariable Long opportunityId, @Valid @RequestBody RoundRequest request) {
        return career.scheduleRound(opportunityId, request.stageIndex(), request.startsAt(), request.venue());
    }

    @GetMapping("/manage/opportunities/{opportunityId}/applications")
    @PreAuthorize(MANAGER)
    @Operation(summary = "An opportunity's applicants", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ApplicationView> applicants(@PathVariable Long opportunityId) {
        return career.applicants(opportunityId);
    }

    @PostMapping("/manage/applications/{applicationId}/move")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Move an applicant to the next stage, or reject", description = "Passing the last stage selects them.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ApplicationView move(@PathVariable Long applicationId, @RequestBody MoveRequest request) {
        return career.move(applicationId, request.advance());
    }

    @PostMapping("/manage/applications/{applicationId}/offer")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Make an offer to a selected applicant", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ApplicationView offer(@PathVariable Long applicationId, @Valid @RequestBody OfferRequest request) {
        return career.makeOffer(applicationId, request.roleTitle(), request.compensation(), request.respondBy());
    }

    @GetMapping("/manage/policy")
    @PreAuthorize(MANAGER)
    @Operation(summary = "The placement policy", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PolicyBody policy() {
        return new PolicyBody(career.oneAcceptedPlacement());
    }

    @PutMapping("/manage/policy")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Change the placement policy", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public PolicyBody setPolicy(@RequestBody PolicyBody body) {
        return new PolicyBody(career.setPolicy(body.oneAcceptedPlacement()));
    }

    @GetMapping(value = "/manage/opportunities/{opportunityId}/applications/export.csv", produces = "text/csv")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Export applicants who consented to share", description = "CSV, built per request and never stored; formula-like cells neutralised.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<String> export(@PathVariable Long opportunityId) {
        List<List<String>> rows = career.exportRows(opportunityId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("applicants-" + opportunityId + ".csv").build().toString())
                .body(ReportCsv.write(rows.get(0), rows.subList(1, rows.size())));
    }
}
