package com.stacc.backend.lms.ai.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.teacher.AiTeacherDocumentService;
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

@RestController
@RequestMapping("/api/v1/lms/faculty")
@Tag(name = "LMS AI teaching tools")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or document does not exist or is not in a course this "
        + "faculty member teaches. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiTeacherDocumentController {

    private final AiTeacherDocumentService documents;

    public AiTeacherDocumentController(AiTeacherDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/courses/{lmsCourseId}/ai/teacher-documents")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(summary = "List the course's teacher-only documents",
            description = "The active documents, oldest first. Archived ones are not listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The documents.")
    public List<AiTeacherDocumentResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return documents.list(AuthenticatedAccount.idFrom(accessToken), lmsCourseId).stream()
                .map(AiTeacherDocumentResponse::of).toList();
    }

    @PostMapping("/courses/{lmsCourseId}/ai/teacher-documents")
    @PreAuthorize(AiAccess.FACULTY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a teacher-only document",
            description = "Plain text up to 20,000 characters. AI Tools use it after the next knowledge refresh.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new document.")
    @ApiResponse(responseCode = "400", description = "The title or text is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The course already has 50 active teacher documents.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiTeacherDocumentResponse create(@PathVariable Long lmsCourseId,
            @Valid @RequestBody AiTeacherDocumentRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return AiTeacherDocumentResponse.of(documents.create(AuthenticatedAccount.idFrom(accessToken), lmsCourseId,
                request.title(), request.content()));
    }

    @PutMapping("/ai/teacher-documents/{documentId}")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(summary = "Edit a teacher-only document",
            description = "Changes the title and text. The course is taken from the stored document.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed document.")
    @ApiResponse(responseCode = "400", description = "The title or text is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The document is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiTeacherDocumentResponse update(@PathVariable Long documentId,
            @Valid @RequestBody AiTeacherDocumentRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return AiTeacherDocumentResponse.of(documents.update(AuthenticatedAccount.idFrom(accessToken), documentId,
                request.title(), request.content()));
    }

    @PostMapping("/ai/teacher-documents/{documentId}/archive")
    @PreAuthorize(AiAccess.FACULTY)
    @Operation(summary = "Archive a teacher-only document",
            description = "AI Tools stop using it after the next refresh. The record is kept; archiving again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived document.")
    public AiTeacherDocumentResponse archive(@PathVariable Long documentId, @AuthenticationPrincipal Jwt accessToken) {
        return AiTeacherDocumentResponse.of(documents.archive(AuthenticatedAccount.idFrom(accessToken), documentId));
    }
}
