package com.stacc.backend.lms.api;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.content.LmsStudyMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lms")
@Tag(name = "LMS")
public class LmsMaterialDownloadController {

    private final LmsStudyMaterialService materialService;

    public LmsMaterialDownloadController(LmsStudyMaterialService materialService) {
        this.materialService = materialService;
    }

    // The rule lets in a student or a faculty account that may view LMS courses. Whether this caller may
    // have this file is decided by the service, through whichever of the two rules the token allows.
    @GetMapping("/materials/{materialId}/download")
    @PreAuthorize("(hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW'))"
            + " or (hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW'))")
    @Operation(
            summary = "Download a study material",
            description = "Sends the file as an attachment, with its stored name, type, and size. A student may download "
                    + "a published material whose topic and module are published, in a course they may use. A faculty "
                    + "member may download any material that is not archived in a course they teach.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The file, as an attachment.",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The material does not exist or is not available to this account. Both look the same.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "File storage is not available right now.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<InputStreamResource> download(@PathVariable Long materialId, Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        LmsStudyMaterialService.Reader reader = new LmsStudyMaterialService.Reader(
                authorities.contains("ROLE_STUDENT") && authorities.contains("LMS_COURSE_VIEW"),
                authorities.contains("ROLE_FACULTY") && authorities.contains("FACULTY_COURSE_VIEW"));
        Long userAccountId = AuthenticatedAccount.idFrom((Jwt) authentication.getPrincipal());

        LmsStudyMaterialService.Download download = materialService.openForDownload(userAccountId, reader, materialId);
        // Always an attachment, never shown inline, so an uploaded file cannot act as a page of this site.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(download.content()));
    }
}
