package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.content.LmsContentManagementService;
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

/**
 * The faculty side of course content. Every route takes the faculty member from the access token. The
 * rules below only let a faculty account with the right permission ask; whether it may use this course,
 * module, or topic is decided from official teaching assignments on every request. There is no delete:
 * content is archived instead.
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
        description = "The course, module, or topic does not exist or is not one this faculty member teaches. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsFacultyContentController {

    private static final String VIEW = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')";
    private static final String MANAGE = "hasRole('FACULTY') and hasAuthority('LMS_CONTENT_MANAGE')";

    private final LmsFacultyContentQueryService contentQueryService;
    private final LmsContentManagementService contentManagement;

    public LmsFacultyContentController(
            LmsFacultyContentQueryService contentQueryService, LmsContentManagementService contentManagement) {
        this.contentQueryService = contentQueryService;
        this.contentManagement = contentManagement;
    }

    @GetMapping("/courses/{lmsCourseId}/content")
    @PreAuthorize(VIEW)
    @Operation(
            summary = "Get the content of a course the faculty member teaches",
            description = "Returns the course with every module and topic that is not archived, drafts included, "
                    + "in course order. Archived content is kept but not listed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The course and its content.")
    public LmsFacultyCourseContentResponse content(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return contentQueryService.getCourseContent(AuthenticatedAccount.idFrom(accessToken), lmsCourseId);
    }

    @PostMapping("/courses/{lmsCourseId}/modules")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a module",
            description = "Adds a draft module after the course's current modules.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new module.")
    @ApiResponse(responseCode = "400", description = "The title or description is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyModuleResponse createModule(
            @PathVariable Long lmsCourseId,
            @Valid @RequestBody LmsModuleRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyModuleResponse.of(contentManagement.createModule(
                AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.title(), request.description()));
    }

    @PutMapping("/courses/{lmsCourseId}/modules/reorder")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Reorder the modules of a course",
            description = "Takes every module that is not archived, each exactly once, in the new order, and "
                    + "numbers them from 1.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The modules in their new order.")
    @ApiResponse(responseCode = "400", description = "The list is missing a current module, repeats one, or names another.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsFacultyModuleResponse> reorderModules(
            @PathVariable Long lmsCourseId,
            @Valid @RequestBody LmsModuleOrderRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return contentManagement.reorderModules(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.moduleIds())
                .stream().map(LmsFacultyModuleResponse::of).toList();
    }

    @PutMapping("/modules/{moduleId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Edit a module",
            description = "Changes the title and description. Status and position have their own actions.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed module.")
    @ApiResponse(responseCode = "400", description = "The title or description is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyModuleResponse updateModule(
            @PathVariable Long moduleId,
            @Valid @RequestBody LmsModuleRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyModuleResponse.of(contentManagement.updateModule(
                AuthenticatedAccount.idFrom(accessToken), moduleId, request.title(), request.description()));
    }

    @PostMapping("/modules/{moduleId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish a module",
            description = "Makes the module visible to students. Its topics keep their own status. Publishing a "
                    + "published module changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published module.")
    @ApiResponse(responseCode = "409", description = "The module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyModuleResponse publishModule(@PathVariable Long moduleId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyModuleResponse.of(contentManagement.publishModule(AuthenticatedAccount.idFrom(accessToken), moduleId));
    }

    @PostMapping("/modules/{moduleId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive a module",
            description = "Hides the module and its topics from students and from the content list. Nothing is "
                    + "deleted. Archiving an archived module changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived module.")
    public LmsFacultyModuleResponse archiveModule(@PathVariable Long moduleId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyModuleResponse.of(contentManagement.archiveModule(AuthenticatedAccount.idFrom(accessToken), moduleId));
    }

    @PostMapping("/modules/{moduleId}/topics")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a topic",
            description = "Adds a draft topic after the module's current topics.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new topic.")
    @ApiResponse(responseCode = "400", description = "The title or content is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyTopicResponse createTopic(
            @PathVariable Long moduleId,
            @Valid @RequestBody LmsTopicRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyTopicResponse.of(contentManagement.createTopic(
                AuthenticatedAccount.idFrom(accessToken), moduleId, request.title(), request.content()));
    }

    @PutMapping("/modules/{moduleId}/topics/reorder")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Reorder the topics of a module",
            description = "Takes every topic of the module that is not archived, each exactly once, in the new "
                    + "order, and numbers them from 1.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The topics in their new order.")
    @ApiResponse(responseCode = "400", description = "The list is missing a current topic, repeats one, or names another.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<LmsFacultyTopicResponse> reorderTopics(
            @PathVariable Long moduleId,
            @Valid @RequestBody LmsTopicOrderRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return contentManagement.reorderTopics(AuthenticatedAccount.idFrom(accessToken), moduleId, request.topicIds())
                .stream().map(LmsFacultyTopicResponse::of).toList();
    }

    @PutMapping("/topics/{topicId}")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Edit a topic",
            description = "Changes the title and text. Status and position have their own actions.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed topic.")
    @ApiResponse(responseCode = "400", description = "The title or content is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The topic or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyTopicResponse updateTopic(
            @PathVariable Long topicId,
            @Valid @RequestBody LmsTopicRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyTopicResponse.of(contentManagement.updateTopic(
                AuthenticatedAccount.idFrom(accessToken), topicId, request.title(), request.content()));
    }

    @PostMapping("/topics/{topicId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Publish a topic",
            description = "Students see the topic once its module is published too. Publishing a published topic "
                    + "changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The published topic.")
    @ApiResponse(responseCode = "409", description = "The topic or its module is archived.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public LmsFacultyTopicResponse publishTopic(@PathVariable Long topicId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyTopicResponse.of(contentManagement.publishTopic(AuthenticatedAccount.idFrom(accessToken), topicId));
    }

    @PostMapping("/topics/{topicId}/archive")
    @PreAuthorize(MANAGE)
    @Operation(
            summary = "Archive a topic",
            description = "Hides the topic from students and from the content list. Nothing is deleted. Archiving "
                    + "an archived topic changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The archived topic.")
    public LmsFacultyTopicResponse archiveTopic(@PathVariable Long topicId, @AuthenticationPrincipal Jwt accessToken) {
        return LmsFacultyTopicResponse.of(contentManagement.archiveTopic(AuthenticatedAccount.idFrom(accessToken), topicId));
    }
}
