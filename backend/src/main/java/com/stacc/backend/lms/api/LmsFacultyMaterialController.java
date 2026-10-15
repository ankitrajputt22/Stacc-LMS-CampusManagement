package com.stacc.backend.lms.api;

import java.util.List;

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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Faculty changes to study materials. The same rule as the other content changes: a faculty account with
 * the content permission may ask, and whether it teaches the course behind the topic or material is
 * decided from official teaching assignments on every request. There is no delete and no file replacement:
 * a wrong file is archived and a new one uploaded.
 */
@RestController
@RequestMapping("/api/lms/faculty")
@Tag(name = "LMS")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "404",
        description = "The topic or material does not exist or is not in a course this faculty member teaches. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsFacultyMaterialController {

    private static final String MANAGE = "hasRole('FACULTY') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsStudyMaterialService materialService;

    public LmsFacultyMaterialController(LmsStudyMaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(value = "/topics/{topicId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Upload a study material",
            description = "Adds a draft material with a file at the end of the topic. Send multipart/form-data with "
                    + "title, an optional description, and file. Allowed: PDF, TXT, DOC, DOCX, PPT, PPTX, XLS, XLSX, "
                    + "PNG, and JPEG, up to 25 MB by default. The file is kept in private storage.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new material.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long, or the file is empty, too large, of a type that is not allowed, or not what its name says.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The topic or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "The upload is larger than the server accepts.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "File storage is not available right now. Nothing was saved.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyMaterialResponse upload(
            @PathVariable Long topicId,
            @Valid @ModelAttribute LmsMaterialUploadRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyMaterialResponse.of(materialService.upload(
                AuthenticatedAccount.idFrom(accessToken), topicId, request.title(), request.description(), request.file()));
    }

    @PutMapping("/topics/{topicId}/materials/reorder")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Reorder the study materials of a topic",
            description = "Takes every material of the topic that is not archived, each exactly once, in the new order, "
                    + "and numbers them from 1.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The materials in their new order.")
    @ApiResponse(responseCode = "400", description = "The list is missing a current material, repeats one, or names another.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The topic or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsFacultyMaterialResponse> reorder(
            @PathVariable Long topicId,
            @Valid @RequestBody LmsMaterialOrderRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return materialService.reorderMaterials(AuthenticatedAccount.idFrom(accessToken), topicId, request.materialIds())
                .stream().map(LmsFacultyMaterialResponse::of).toList();
    }

    @PutMapping("/materials/{materialId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Edit a study material",
            description = "Changes the title and description. The file, status, and position cannot be changed here.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed material.")
    @ApiResponse(responseCode = "400", description = "The title or description is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The material, its topic, or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyMaterialResponse update(
            @PathVariable Long materialId,
            @Valid @RequestBody LmsMaterialRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyMaterialResponse.of(materialService.updateMaterial(
                AuthenticatedAccount.idFrom(accessToken), materialId, request.title(), request.description()));
    }

    @PostMapping("/materials/{materialId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish a study material",
            description = "Students see it once its topic and module are published too. Publishing a published material "
                    + "changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published material.")
    @ApiResponse(responseCode = "409", description = "The material, its topic, or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyMaterialResponse publish(@PathVariable Long materialId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyMaterialResponse.of(materialService.publishMaterial(AuthenticatedAccount.idFrom(accessToken), materialId));
    }

    @PostMapping("/materials/{materialId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive a study material",
            description = "Hides the material from students and from the content list. The record and the stored file "
                    + "are kept. Archiving an archived material changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived material.")
    public LmsFacultyMaterialResponse archive(@PathVariable Long materialId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyMaterialResponse.of(materialService.archiveMaterial(AuthenticatedAccount.idFrom(accessToken), materialId));
    }
}
