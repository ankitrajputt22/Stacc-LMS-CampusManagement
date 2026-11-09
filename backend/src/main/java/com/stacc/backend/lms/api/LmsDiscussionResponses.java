package com.stacc.backend.lms.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.discussion.LmsDiscussionReply;
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

    @Schema(description = "A thread in the course's list: pinned first, then the most recently active.")
    public record ThreadSummary(Long threadId, String title, String authorLoginId, AuthorKind authorKind, boolean pinned,
            boolean answered, long replyCount, Instant createdAt, Instant lastActivityAt) {

        static ThreadSummary of(LmsDiscussionService.ThreadSummary summary) {
            LmsDiscussionThread t = summary.thread();
            return new ThreadSummary(t.getId(), t.getTitle(), t.getAuthor().getLoginId(), t.getAuthorKind(), t.isPinned(),
                    t.getAcceptedReplyId() != null, summary.replies(), t.getCreatedAt(), t.getLastActivityAt());
        }
    }

    @Schema(description = "A reply, oldest first.")
    public record Reply(Long replyId, String authorLoginId, AuthorKind authorKind, String body, Instant createdAt, boolean accepted) {
    }

    @Schema(description = "A thread with its replies. canAccept says whether the caller may accept an answer; canPin whether they may pin.")
    public record ThreadDetail(Long threadId, String title, String body, String authorLoginId, AuthorKind authorKind,
            boolean pinned, Long acceptedReplyId, Instant createdAt, boolean canAccept, boolean canPin, List<Reply> replies) {

        static ThreadDetail of(LmsDiscussionService.ThreadDetail detail, Viewer viewer) {
            LmsDiscussionThread t = detail.thread();
            boolean faculty = viewer.kind() == AuthorKind.FACULTY;
            boolean asker = t.getAuthorKind() == AuthorKind.STUDENT && viewer.kind() == AuthorKind.STUDENT
                    && t.getAuthor().getId().equals(viewer.accountId());
            List<Reply> replies = detail.replies().stream().map((LmsDiscussionReply r) -> new Reply(r.getId(),
                    r.getAuthor().getLoginId(), r.getAuthorKind(), r.getBody(), r.getCreatedAt(),
                    r.getId().equals(t.getAcceptedReplyId()))).toList();
            return new ThreadDetail(t.getId(), t.getTitle(), t.getBody(), t.getAuthor().getLoginId(), t.getAuthorKind(),
                    t.isPinned(), t.getAcceptedReplyId(), t.getCreatedAt(), faculty || asker, faculty, replies);
        }
    }
}
