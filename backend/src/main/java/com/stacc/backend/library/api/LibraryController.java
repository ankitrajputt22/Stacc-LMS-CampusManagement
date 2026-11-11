package com.stacc.backend.library.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.library.LibraryService;
import com.stacc.backend.library.LibraryService.CopyView;
import com.stacc.backend.library.LibraryService.DeskLoan;
import com.stacc.backend.library.LibraryService.LoanView;
import com.stacc.backend.library.LibraryService.MyLibrary;
import com.stacc.backend.library.LibraryService.ResourceView;
import com.stacc.backend.library.LibraryService.TitleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The library: the catalog, loans, holds, and digital resources for members, and the desk for librarians. */
@RestController
@RequestMapping("/api/v1/library")
@Tag(name = "Library")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "No such title, copy, loan, hold, or resource for the signed-in account.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LibraryController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('LIBRARY_USE')";
    private static final String MANAGE = "hasRole('LIBRARIAN') and hasAuthority('LIBRARY_MANAGE')";
    private static final String ANY = "(" + USE + ") or (" + MANAGE + ")";

    private final LibraryService service;

    public LibraryController(LibraryService service) {
        this.service = service;
    }

    public record TitleRequest(@NotBlank @Size(max = 300) String title, @Size(max = 300) String authors, @Size(max = 20) String isbn,
            @Size(max = 200) String publisher, Integer publishedYear, @Size(max = 150) String subject) {
    }

    public record CopyRequest(@NotBlank @Size(max = 40) String barcode, @Size(max = 50) String shelf) {
    }

    public record IssueRequest(@NotBlank @Size(max = 40) String barcode, @NotBlank @Size(max = 100) String loginId) {
    }

    public record BarcodeRequest(@NotBlank @Size(max = 40) String barcode) {
    }

    public record ResourceRequest(@NotBlank @Size(max = 200) String title, @Size(max = 1000) String description, @NotBlank @Size(max = 500) String url) {
    }

    public record ActiveRequest(boolean active) {
    }

    @GetMapping("/titles")
    @PreAuthorize(ANY)
    @Operation(summary = "Search the catalog", description = "By title, authors, subject, or ISBN; up to 50 titles with availability and the queue.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TitleView> search(@RequestParam(defaultValue = "") String q, @AuthenticationPrincipal Jwt token) {
        return service.search(AuthenticatedAccount.idFrom(token), q, false);
    }

    @GetMapping("/me")
    @PreAuthorize(USE)
    @Operation(summary = "My loans and holds", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public MyLibrary mine(@AuthenticationPrincipal Jwt token) {
        return service.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/titles/{titleId}/hold")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Join a title's queue", description = "Holds come before lending to others, in the order placed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void hold(@PathVariable Long titleId, @AuthenticationPrincipal Jwt token) {
        service.placeHold(AuthenticatedAccount.idFrom(token), titleId);
    }

    @PostMapping("/holds/{holdId}/cancel")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a title's queue", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void cancelHold(@PathVariable Long holdId, @AuthenticationPrincipal Jwt token) {
        service.cancelHold(AuthenticatedAccount.idFrom(token), holdId);
    }

    @PostMapping("/loans/{loanId}/renew")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Renew my loan", description = "Once, before it is due, when nobody is waiting for the title.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void renew(@PathVariable Long loanId, @AuthenticationPrincipal Jwt token) {
        service.renew(AuthenticatedAccount.idFrom(token), loanId);
    }

    @GetMapping("/resources")
    @PreAuthorize(ANY)
    @Operation(summary = "Digital resources", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ResourceView> resources() {
        return service.resources(false);
    }

    // --- The desk

    @GetMapping("/desk/titles")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Search the whole catalog", description = "Inactive titles included.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<TitleView> deskSearch(@RequestParam(defaultValue = "") String q, @AuthenticationPrincipal Jwt token) {
        return service.search(AuthenticatedAccount.idFrom(token), q, true);
    }

    @PostMapping("/desk/titles")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a title", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public TitleView addTitle(@Valid @RequestBody TitleRequest request) {
        return service.addTitle(request.title(), request.authors(), request.isbn(), request.publisher(), request.publishedYear(), request.subject());
    }

    @PostMapping("/desk/titles/{titleId}/active")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Make a title active or inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void setTitleActive(@PathVariable Long titleId, @RequestBody ActiveRequest request) {
        service.setTitleActive(titleId, request.active());
    }

    @GetMapping("/desk/titles/{titleId}/copies")
    @PreAuthorize(MANAGE)
    @Operation(summary = "A title's copies", description = "With who has each one on loan.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<CopyView> copies(@PathVariable Long titleId) {
        return service.copiesOf(titleId);
    }

    @PostMapping("/desk/titles/{titleId}/copies")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add a copy", description = "With a unique barcode.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void addCopy(@PathVariable Long titleId, @Valid @RequestBody CopyRequest request) {
        service.addCopy(titleId, request.barcode(), request.shelf());
    }

    @PostMapping("/desk/copies/withdraw")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw a copy", description = "Not while on loan; the record is kept.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void withdraw(@Valid @RequestBody BarcodeRequest request) {
        service.withdrawCopy(request.barcode());
    }

    @PostMapping("/desk/issue")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lend a copy", description = "To an active student or faculty account, within five loans and the title's queue.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "On loan, over the limit, or held for others.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LoanView issue(@Valid @RequestBody IssueRequest request, @AuthenticationPrincipal Jwt token) {
        return service.issue(AuthenticatedAccount.idFrom(token), request.barcode(), request.loginId());
    }

    @PostMapping("/desk/return")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Take a copy back", description = "Answers the borrower's login ID.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public String giveBack(@Valid @RequestBody BarcodeRequest request) {
        return service.giveBack(request.barcode());
    }

    @GetMapping("/desk/loans")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Open loans", description = "Soonest due first, with overdue worked out now.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<DeskLoan> openLoans() {
        return service.openLoans();
    }

    @GetMapping("/desk/resources")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Every digital resource", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ResourceView> allResources() {
        return service.resources(true);
    }

    @PostMapping("/desk/resources")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a digital resource", description = "One https:// address.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResourceView addResource(@Valid @RequestBody ResourceRequest request) {
        return service.addResource(request.title(), request.description(), request.url());
    }

    @PostMapping("/desk/resources/{resourceId}/active")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Make a resource active or inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void setResourceActive(@PathVariable Long resourceId, @RequestBody ActiveRequest request) {
        service.setResourceActive(resourceId, request.active());
    }
}
