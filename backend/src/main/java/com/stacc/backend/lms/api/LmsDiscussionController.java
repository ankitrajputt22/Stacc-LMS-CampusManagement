package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.api.LmsDiscussionResponses.HideRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.LockRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.PinRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ReplyRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.Report;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ReportRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ResolveRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ThreadDetail;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ThreadRequest;
import com.stacc.backend.lms.api.LmsDiscussionResponses.ThreadSummary;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.discussion.LmsDiscussionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Course discussions. Students use /courses/{id}/discussions under the student access policy, faculty
 * /faculty/courses/{id}/discussions under the faculty access policy; the route decides the kind, the token the account.
 */
@RestController
@RequestMapping("/api/v1/lms")
@Tag(name = "LMS course discussions")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course or discussion is not available to the account. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class LmsDiscussionController {

    private static final String STUDENT = "hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_DISCUSSION_USE')";
    private static final String FACULTY = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_DISCUSSION_USE')";
    private static final String MODERATE = "hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_DISCUSSION_MODERATE')";
    private static final String STUDENT_PATH = "/courses/{lmsCourseId}/discussions";
    private static final String FACULTY_PATH = "/faculty/courses/{lmsCourseId}/discussions";

    private final LmsDiscussionService discussions;

    public LmsDiscussionController(LmsDiscussionService discussions) {
        this.discussions = discussions;
    }

    @GetMapping(STUDENT_PATH)
    @PreAuthorize(STUDENT)
    @Operation(summary = "List a course's discussions as a student", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ThreadSummary> studentList(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return list(student(token), lmsCourseId);
    }

    @GetMapping(FACULTY_PATH)
    @PreAuthorize(FACULTY)
    @Operation(summary = "List a course's discussions as its faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<ThreadSummary> facultyList(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return list(faculty(token), lmsCourseId);
    }

    @PostMapping(STUDENT_PATH)
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a discussion as a student", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail studentStart(@PathVariable Long lmsCourseId, @Valid @RequestBody ThreadRequest request, @AuthenticationPrincipal Jwt token) {
        return start(student(token), lmsCourseId, request);
    }

    @PostMapping(FACULTY_PATH)
    @PreAuthorize(FACULTY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a discussion as the course's faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail facultyStart(@PathVariable Long lmsCourseId, @Valid @RequestBody ThreadRequest request, @AuthenticationPrincipal Jwt token) {
        return start(faculty(token), lmsCourseId, request);
    }

    @GetMapping(STUDENT_PATH + "/{threadId}")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Read a discussion with its replies as a student", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail studentDetail(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @AuthenticationPrincipal Jwt token) {
        Viewer viewer = student(token);
        return ThreadDetail.of(discussions.detail(viewer, lmsCourseId, threadId), viewer);
    }

    @GetMapping(FACULTY_PATH + "/{threadId}")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Read a discussion with its replies as the course's faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail facultyDetail(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.detail(viewer, lmsCourseId, threadId), viewer);
    }

    @PostMapping(STUDENT_PATH + "/{threadId}/replies")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Reply as a student", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail studentReply(@PathVariable Long lmsCourseId, @PathVariable Long threadId,
            @Valid @RequestBody ReplyRequest request, @AuthenticationPrincipal Jwt token) {
        Viewer viewer = student(token);
        return ThreadDetail.of(discussions.reply(viewer, lmsCourseId, threadId, request.body()), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/replies")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Reply as the course's faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail facultyReply(@PathVariable Long lmsCourseId, @PathVariable Long threadId,
            @Valid @RequestBody ReplyRequest request, @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.reply(viewer, lmsCourseId, threadId, request.body()), viewer);
    }

    @PostMapping(STUDENT_PATH + "/{threadId}/replies/{replyId}/accept")
    @PreAuthorize(STUDENT)
    @Operation(summary = "Accept a reply as the answer to your own question", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail studentAccept(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @PathVariable Long replyId,
            @AuthenticationPrincipal Jwt token) {
        Viewer viewer = student(token);
        return ThreadDetail.of(discussions.accept(viewer, lmsCourseId, threadId, replyId), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/replies/{replyId}/accept")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Accept a reply as the answer, as the course's faculty", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail facultyAccept(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @PathVariable Long replyId,
            @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.accept(viewer, lmsCourseId, threadId, replyId), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/pin")
    @PreAuthorize(FACULTY)
    @Operation(summary = "Pin or unpin a discussion", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail pin(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @Valid @RequestBody PinRequest request,
            @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.pin(viewer, lmsCourseId, threadId, request.pinned()), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/hide")
    @PreAuthorize(MODERATE)
    @Operation(summary = "Hide or show a discussion",
            description = "A hidden thread is kept but no longer shown to students; hiding it resolves its open reports.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail hideThread(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @Valid @RequestBody HideRequest request,
            @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.hideThread(viewer, lmsCourseId, threadId, request.hidden()), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/lock")
    @PreAuthorize(MODERATE)
    @Operation(summary = "Lock or open a discussion", description = "A locked thread takes no new replies.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail lockThread(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @Valid @RequestBody LockRequest request,
            @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.lockThread(viewer, lmsCourseId, threadId, request.locked()), viewer);
    }

    @PostMapping(FACULTY_PATH + "/{threadId}/replies/{replyId}/hide")
    @PreAuthorize(MODERATE)
    @Operation(summary = "Hide or show a reply",
            description = "Students see a hidden reply only as hidden; it stops being the accepted answer, and its open reports are resolved.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ThreadDetail hideReply(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @PathVariable Long replyId,
            @Valid @RequestBody HideRequest request, @AuthenticationPrincipal Jwt token) {
        Viewer viewer = faculty(token);
        return ThreadDetail.of(discussions.hideReply(viewer, lmsCourseId, threadId, replyId, request.hidden()), viewer);
    }

    @PostMapping(STUDENT_PATH + "/{threadId}/reports")
    @PreAuthorize(STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a post to the course's faculty",
            description = "The thread, or one of its replies, with a reason. Faculty see the report without the reporter's ID.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The report was sent.")
    @ApiResponse(responseCode = "409", description = "It is the reporter's own post, or they already reported it.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void report(@PathVariable Long lmsCourseId, @PathVariable Long threadId, @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt token) {
        discussions.report(student(token), lmsCourseId, threadId, request.replyId(), request.reason());
    }

    @GetMapping("/faculty/courses/{lmsCourseId}/discussion-reports")
    @PreAuthorize(MODERATE)
    @Operation(summary = "Open reports of a course's discussions", description = "Oldest first, without the reporters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<Report> reports(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt token) {
        return discussions.openReports(faculty(token), lmsCourseId).stream().map(Report::of).toList();
    }

    @PostMapping("/faculty/courses/{lmsCourseId}/discussion-reports/{reportId}/resolve")
    @PreAuthorize(MODERATE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Resolve a report",
            description = "Hides the reported post, or dismisses its open reports. Every open report of the same post is resolved together.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Resolved.")
    public void resolve(@PathVariable Long lmsCourseId, @PathVariable Long reportId, @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal Jwt token) {
        discussions.resolve(faculty(token), lmsCourseId, reportId, request.hide());
    }

    private List<ThreadSummary> list(Viewer viewer, Long lmsCourseId) {
        return discussions.list(viewer, lmsCourseId).stream().map(ThreadSummary::of).toList();
    }

    private ThreadDetail start(Viewer viewer, Long lmsCourseId, ThreadRequest request) {
        return ThreadDetail.of(discussions.start(viewer, lmsCourseId, request.title(), request.body()), viewer);
    }

    static Viewer student(Jwt token) {
        return new Viewer(AuthenticatedAccount.idFrom(token), AuthorKind.STUDENT);
    }

    static Viewer faculty(Jwt token) {
        return new Viewer(AuthenticatedAccount.idFrom(token), AuthorKind.FACULTY);
    }
}
