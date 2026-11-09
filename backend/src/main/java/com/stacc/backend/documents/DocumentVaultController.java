package com.stacc.backend.documents;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.documents.DocumentVaultService.Authenticity;
import com.stacc.backend.documents.DocumentVaultService.DocumentView;
import com.stacc.backend.documents.DocumentVaultService.RequestView;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The person's own document vault and certificate requests, the college's certificate desk, and the public check. */
@RestController
@Tag(name = "Documents and certificates")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class DocumentVaultController {

    private static final String OWNER = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('DOCUMENT_VAULT_USE')";
    private static final String ISSUER = "hasRole('ADMIN') and hasAuthority('DOCUMENT_ISSUE')";

    public record CertificateRequestBody(@NotNull CertificateRequest.Type type, @NotBlank @Size(max = 500) String purpose) {
    }

    public record RefusalBody(@NotBlank @Size(max = 500) String note) {
    }

    private final DocumentVaultService vault;

    public DocumentVaultController(DocumentVaultService vault) {
        this.vault = vault;
    }

    @GetMapping("/api/v1/me/documents")
    @PreAuthorize(OWNER)
    @Operation(summary = "Your documents", description = "Issued certificates (with their verification codes) and your uploads.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<DocumentView> mine(@AuthenticationPrincipal Jwt token) {
        return vault.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping(value = "/api/v1/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a document to your vault", description = "multipart/form-data with title and one file.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public DocumentView upload(@AuthenticationPrincipal Jwt token, @RequestParam String title,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return vault.upload(AuthenticatedAccount.idFrom(token), title, file);
    }

    @GetMapping("/api/v1/me/documents/{documentId}/download")
    @PreAuthorize(OWNER)
    @Operation(summary = "Download one of your documents", description = "Always an attachment.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Jwt token, @PathVariable Long documentId) {
        LmsAttachmentStore.Download file = vault.open(AuthenticatedAccount.idFrom(token), documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.content()));
    }

    @GetMapping("/api/v1/me/certificate-requests")
    @PreAuthorize(OWNER)
    @Operation(summary = "Your certificate requests", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<RequestView> myRequests(@AuthenticationPrincipal Jwt token) {
        return vault.myRequests(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/api/v1/me/certificate-requests")
    @PreAuthorize(OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a certificate", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public RequestView request(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CertificateRequestBody body) {
        return vault.request(AuthenticatedAccount.idFrom(token), body.type(), body.purpose());
    }

    @PostMapping("/api/v1/me/certificate-requests/{requestId}/cancel")
    @PreAuthorize(OWNER)
    @Operation(summary = "Cancel your certificate request", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public RequestView cancel(@AuthenticationPrincipal Jwt token, @PathVariable Long requestId) {
        return vault.cancel(AuthenticatedAccount.idFrom(token), requestId);
    }

    @GetMapping("/api/v1/admin/certificate-requests")
    @PreAuthorize(ISSUER)
    @Operation(summary = "Certificate requests", description = "Oldest first; filter by status.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<RequestView> queue(@RequestParam(required = false) CertificateRequest.Status status) {
        return vault.queue(status);
    }

    @PostMapping(value = "/api/v1/admin/certificate-requests/{requestId}/issue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ISSUER)
    @Operation(summary = "Issue a certificate into the requester's vault", description = "multipart/form-data with title and the file.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public RequestView issue(@AuthenticationPrincipal Jwt token, @PathVariable Long requestId, @RequestParam String title,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return vault.issue(AuthenticatedAccount.idFrom(token), requestId, title, file);
    }

    @PostMapping("/api/v1/admin/certificate-requests/{requestId}/refuse")
    @PreAuthorize(ISSUER)
    @Operation(summary = "Refuse a certificate request with a note", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public RequestView refuse(@AuthenticationPrincipal Jwt token, @PathVariable Long requestId, @Valid @RequestBody RefusalBody body) {
        return vault.refuse(AuthenticatedAccount.idFrom(token), requestId, body.note());
    }

    @PostMapping("/api/v1/admin/documents/{documentId}/withdraw")
    @PreAuthorize(ISSUER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw an issued document", description = "Its verification code stops confirming it.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void withdraw(@PathVariable Long documentId) {
        vault.withdraw(documentId);
    }

    /**
     * Public on purpose (listed in SecurityConfig): anyone given a verification code, such as an employer, can confirm a
     * document is genuine. It reveals only authenticity, the title, and the issue date; never the person or the file.
     */
    @GetMapping("/api/v1/public/documents/verify/{code}")
    @Operation(summary = "Check whether a verification code belongs to an authentic document")
    public Authenticity verify(@PathVariable String code) {
        return vault.verify(code);
    }
}
