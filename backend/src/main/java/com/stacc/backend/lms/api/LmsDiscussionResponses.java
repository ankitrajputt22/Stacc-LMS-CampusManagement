package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.discussion.LmsDiscussionReply;
import com.stacc.backend.lms.discussion.LmsDiscussionReport;
import com.stacc.backend.lms.discussion.LmsDiscussionService;
import com.stacc.backend.lms.discussion.LmsDiscussionThread;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requests and responses of course discussions. Authors are shown by login ID only, never by name or contact. */
public final class LmsDiscussionResponses {

    private LmsDiscussionResponses() {
    }

    @Schema(description = "A new discussion thread.")
    public record ThreadRequest(@NotBlank @Size(max = LmsDiscussionThread.MAX_TITLE_LENGTH) String title,
            @NotBlank @Size(max = LmsDiscussionThread.MAX_BODY_LENGTH) String body) {
    }

    @Schema(description = "A reply in a thread.")
    public record ReplyRequest(@NotBlank @Size(max = LmsDiscussionThread.MAX_BODY_LENGTH) String body) {
    }

    @Schema(description = "Whether a thread is pinned.")
    public record PinRequest(@NotNull Boolean pinned) {
    }

    @Schema(description = "Hide or show a post again.")
    public record HideRequest(@NotNull Boolean hidden) {
    }

    @Schema(description = "Lock a thread against new replies, or open it again.")
    public record LockRequest(@NotNull Boolean locked) {
    }

    @Schema(description = "A report of a thread (no replyId) or one of its replies, with a reason.")
    public record ReportRequest(Long replyId, @NotBlank @Size(max = LmsDiscussionReport.MAX_REASON_LENGTH) String reason) {
    }

    @Schema(description = "How faculty resolve a report: hide the post, or dismiss its open reports.")
    public record ResolveRequest(@NotNull Boolean hide) {
    }

    @Schema(description = "A thread in the course's list: pinned first, then the most recently active. Students never see hidden threads.")
    public record ThreadSummary(Long threadId, String title, String authorLoginId, AuthorKind authorKind, boolean pinned,
            boolean answered, long replyCount, Instant createdAt, Instant lastActivityAt, boolean locked, boolean hidden) {

        static ThreadSummary of(LmsDiscussionService.ThreadSummary summary) {
            LmsDiscussionThread t = summary.thread();
            return new ThreadSummary(t.getId(), t.getTitle(), t.getAuthor().getLoginId(), t.getAuthorKind(), t.isPinned(),
                    t.getAcceptedReplyId() != null, summary.replies(), t.getCreatedAt(), t.getLastActivityAt(), t.isLocked(),
                    t.isHidden());
        }
    }

    @Schema(description = "A reply, oldest first. A hidden reply reaches students without its author or text.")
    public record Reply(Long replyId, String authorLoginId, AuthorKind authorKind, String body, Instant createdAt, boolean accepted,
            boolean hidden) {
    }

    @Schema(description = "A thread with its replies. canAccept says whether the caller may accept an answer; canPin and "
            + "canModerate whether they may pin, hide, and lock; canReport whether they may report posts.")
    public record ThreadDetail(Long threadId, String title, String body, String authorLoginId, AuthorKind authorKind,
            boolean pinned, Long acceptedReplyId, Instant createdAt, boolean canAccept, boolean canPin, List<Reply> replies,
            boolean locked, boolean hidden, boolean canModerate, boolean canReport) {

        static ThreadDetail of(LmsDiscussionService.ThreadDetail detail, Viewer viewer) {
            LmsDiscussionThread t = detail.thread();
            boolean faculty = viewer.kind() == AuthorKind.FACULTY;
            boolean asker = t.getAuthorKind() == AuthorKind.STUDENT && viewer.kind() == AuthorKind.STUDENT
                    && t.getAuthor().getId().equals(viewer.accountId());
            List<Reply> replies = detail.replies().stream().map((LmsDiscussionReply r) -> r.isHidden() && !faculty
                    ? new Reply(r.getId(), null, null, null, r.getCreatedAt(), false, true)
                    : new Reply(r.getId(), r.getAuthor().getLoginId(), r.getAuthorKind(), r.getBody(), r.getCreatedAt(),
                            r.getId().equals(t.getAcceptedReplyId()), r.isHidden())).toList();
            return new ThreadDetail(t.getId(), t.getTitle(), t.getBody(), t.getAuthor().getLoginId(), t.getAuthorKind(),
                    t.isPinned(), t.getAcceptedReplyId(), t.getCreatedAt(), faculty || asker, faculty, replies, t.isLocked(),
                    t.isHidden(), faculty, !faculty);
        }
    }

    @Schema(description = "An open report for the course's faculty. Reporters are not shown.")
    public record Report(Long reportId, Long threadId, String threadTitle, Long replyId,
            @Schema(description = "The reported post's author, by login ID.") String postAuthorLoginId,
            @Schema(description = "The start of the reported post's text.") String excerpt,
            String reason, Instant reportedAt, boolean postHidden) {

        private static final int EXCERPT = 200;

        static Report of(LmsDiscussionReport report) {
            LmsDiscussionThread t = report.getThread();
            LmsDiscussionReply r = report.getReply();
            String text = r == null ? t.getBody() : r.getBody();
            return new Report(report.getId(), t.getId(), t.getTitle(), r == null ? null : r.getId(),
                    (r == null ? t.getAuthor() : r.getAuthor()).getLoginId(),
                    text.length() > EXCERPT ? text.substring(0, EXCERPT - 1) + "…" : text, report.getReason(),
                    report.getCreatedAt(), r == null ? t.isHidden() : r.isHidden());
        }
    }
}
