package com.stacc.backend.lms.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.message.LmsConversation;
import com.stacc.backend.lms.message.LmsConversationMessage;
import com.stacc.backend.lms.message.LmsMessageAttachment;
import com.stacc.backend.lms.message.LmsMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Private messages between a student and the faculty of a course. Students see their own conversations; the course's
 * faculty see every conversation of the course. People are shown by login ID only.
 */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS course messages")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or conversation is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsMessageController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_MESSAGE_USE')";
    private static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_MESSAGE_USE')";
    private static final String STUDENT_PATH = "/courses/{lmsCourseId}/conversations";
    private static final String FACULTY_PATH = "/faculty/courses/{lmsCourseId}/conversations";

    @Schema(description = "A conversation in the list, most recent first.")
    public record ConversationSummary(Long conversationId, String subject, String studentLoginId, Instant createdAt, Instant lastMessageAt) {

        static ConversationSummary of(LmsConversation c) {
            return new ConversationSummary(c.getId(), c.getSubject(), c.getStudent().getLoginId(), c.getCreatedAt(), c.getLastMessageAt());
        }
    }

    @Schema(description = "A file sent with a message; download it through the conversation's attachment route.")
    public record Attachment(Long attachmentId, String fileName, String contentType, long sizeBytes) {
    }

    @Schema(description = "A message, oldest first, with its files.")
    public record Message(Long messageId, String authorLoginId, AuthorKind authorKind, String body, Instant createdAt,
            List<Attachment> attachments) {
    }

    @Schema(description = "A conversation with every message.")
    public record ConversationDetail(Long conversationId, String subject, String studentLoginId, Instant createdAt, List<Message> messages) {

        static ConversationDetail of(LmsMessageService.ConversationDetail detail) {
            LmsConversation c = detail.conversation();
            Map<Long, List<Attachment>> files = detail.attachments().stream().collect(Collectors.groupingBy(
                    (LmsMessageAttachment a) -> a.getMessage().getId(), Collectors.mapping(
                            a -> new Attachment(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes()), Collectors.toList())));
            return new ConversationDetail(c.getId(), c.getSubject(), c.getStudent().getLoginId(), c.getCreatedAt(),
                    detail.messages().stream().map(m -> new Message(m.getId(), m.getAuthor().getLoginId(), m.getAuthorKind(),
                            m.getBody(), m.getCreatedAt(), files.getOrDefault(m.getId(), List.of()))).toList());
        }
    }

    private final LmsMessageService messages;

    public LmsMessageController(LmsMessageService messages) {
        this.messages = messages;
    }

    @GetMapping(STUDENT_PATH)
    @PreAuthorize(STUDENT)
    @Operation(summary = "List your conversations with a course's faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ConversationSummary> studentList(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return messages.list(LmsDiscussionController.student(token), lmsCourseId).stream().map(ConversationSummary::of).toList();
    }

    @GetMapping(FACULTY_PATH)
    @PreAuthorize(FACULTY)
    @Operation(summary = "List every student conversation of a course you teach", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ConversationSummary> facultyList(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return messages.list(LmsDiscussionController.faculty(token), lmsCourseId).stream().map(ConversationSummary::of).toList();
    }

    @PostMapping(value = STUDENT_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a conversation with the course's faculty",
            description = "multipart/form-data with subject, body, and up to 5 files (the study-material file rules).",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ConversationDetail start(@PathVariable Long lmsCourseId, @RequestParam(required = false) String subject,
            @RequestParam(required = false) String body, @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Jwt token) {
        return ConversationDetail.of(messages.start(LmsDiscussionController.student(token), lmsCourseId, subject, body, files));
    }

    @GetMapping(STUDENT_PATH + "/{conversationId}")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Read one of your conversations", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ConversationDetail studentDetail(@PathVariable Long lmsCourseId, @PathVariable Long conversationId, @AuthenticationPrincipal Jwt token) {
        return ConversationDetail.of(messages.detail(LmsDiscussionController.student(token), lmsCourseId, conversationId));
    }

    @GetMapping(FACULTY_PATH + "/{conversationId}")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Read a student conversation of a course you teach", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ConversationDetail facultyDetail(@PathVariable Long lmsCourseId, @PathVariable Long conversationId, @AuthenticationPrincipal Jwt token) {
        return ConversationDetail.of(messages.detail(LmsDiscussionController.faculty(token), lmsCourseId, conversationId));
    }

    @PostMapping(value = STUDENT_PATH + "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(STUDENT)
    @Operation(summary = "Send a message in your conversation", description = "multipart/form-data with body and up to 5 files.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ConversationDetail studentSend(@PathVariable Long lmsCourseId, @PathVariable Long conversationId,
            @RequestParam(required = false) String body, @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Jwt token) {
        return ConversationDetail.of(messages.send(LmsDiscussionController.student(token), lmsCourseId, conversationId, body, files));
    }

    @GetMapping(STUDENT_PATH + "/{conversationId}/attachments/{attachmentId}")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Download a file of a conversation", description = "Always an attachment, never shown inline.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<InputStreamResource> studentDownload(@PathVariable Long lmsCourseId, @PathVariable Long conversationId,
            @PathVariable Long attachmentId, @AuthenticationPrincipal Jwt token) {
        return download(messages.openAttachment(LmsDiscussionController.student(token), lmsCourseId, conversationId, attachmentId));
    }

    @PostMapping(value = FACULTY_PATH + "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(FACULTY)
    @Operation(summary = "Answer a student conversation", description = "multipart/form-data with body and up to 5 files.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ConversationDetail facultySend(@PathVariable Long lmsCourseId, @PathVariable Long conversationId,
            @RequestParam(required = false) String body, @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Jwt token) {
        return ConversationDetail.of(messages.send(LmsDiscussionController.faculty(token), lmsCourseId, conversationId, body, files));
    }

    @GetMapping(FACULTY_PATH + "/{conversationId}/attachments/{attachmentId}")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Download a file of a conversation", description = "Always an attachment, never shown inline.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<InputStreamResource> facultyDownload(@PathVariable Long lmsCourseId, @PathVariable Long conversationId,
            @PathVariable Long attachmentId, @AuthenticationPrincipal Jwt token) {
        return download(messages.openAttachment(LmsDiscussionController.faculty(token), lmsCourseId, conversationId, attachmentId));
    }

    private static ResponseEntity<InputStreamResource> download(LmsAttachmentStore.Download file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.content()));
    }
}
