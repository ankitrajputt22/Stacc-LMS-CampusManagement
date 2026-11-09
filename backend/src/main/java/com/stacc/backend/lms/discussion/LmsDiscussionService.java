package com.stacc.backend.lms.discussion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course discussions. A student takes part in a course the student access policy allows, faculty in a course the
 * faculty access policy allows; the kind comes from the route, the account from the access token. Anyone taking part
 * starts threads and replies; faculty pin threads; the thread's author or the faculty accept one reply as the answer.
 * Nothing is edited or deleted. A course or thread the caller may not use answers 404.
 */
@Service
public class LmsDiscussionService {

    public static final String THREAD_UNAVAILABLE = "Discussion not found or unavailable.";
    public static final String REPLY_UNAVAILABLE = "That reply is not part of this discussion.";
    public static final String NOT_YOURS = "Only the person who asked or the course's faculty can accept an answer.";

    public record ThreadSummary(LmsDiscussionThread thread, long replies) {
    }

    public record ThreadDetail(LmsDiscussionThread thread, List<LmsDiscussionReply> replies) {
    }

    private final LmsDiscussionThreadRepository threads;
    private final LmsDiscussionReplyRepository replies;
    private final LmsCourseRepository lmsCourses;
    private final UserAccountRepository accounts;
    private final LmsCourseParticipation participation;
    private final Clock clock;

    public LmsDiscussionService(LmsDiscussionThreadRepository threads, LmsDiscussionReplyRepository replies,
            LmsCourseRepository lmsCourses, UserAccountRepository accounts, LmsCourseParticipation participation,
            Clock clock) {
        this.threads = threads;
        this.replies = replies;
        this.lmsCourses = lmsCourses;
        this.accounts = accounts;
        this.participation = participation;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ThreadSummary> list(Viewer viewer, Long lmsCourseId) {
        requireCourse(viewer, lmsCourseId);
        Map<Long, Long> counts = threads.countRepliesForCourse(lmsCourseId).stream()
                .collect(Collectors.toMap(LmsDiscussionThreadRepository.ReplyCount::getThreadId,
                        LmsDiscussionThreadRepository.ReplyCount::getReplies));
        return threads.findAllForCourse(lmsCourseId).stream()
                .map(thread -> new ThreadSummary(thread, counts.getOrDefault(thread.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ThreadDetail start(Viewer viewer, Long lmsCourseId, String title, String body) {
        requireCourse(viewer, lmsCourseId);
        LmsCourse course = lmsCourses.getReferenceById(lmsCourseId);
        LmsDiscussionThread thread = threads.saveAndFlush(new LmsDiscussionThread(course,
                accounts.getReferenceById(viewer.accountId()), viewer.kind(), title, body, now()));
        return new ThreadDetail(thread, List.of());
    }

    @Transactional(readOnly = true)
    public ThreadDetail detail(Viewer viewer, Long lmsCourseId, Long threadId) {
        LmsDiscussionThread thread = thread(viewer, lmsCourseId, threadId);
        return new ThreadDetail(thread, replies.findAllForThread(thread.getId()));
    }

    @Transactional
    public ThreadDetail reply(Viewer viewer, Long lmsCourseId, Long threadId, String body) {
        thread(viewer, lmsCourseId, threadId);
        LmsDiscussionThread thread = locked(threadId);
        Instant now = now();
        replies.saveAndFlush(new LmsDiscussionReply(thread, accounts.getReferenceById(viewer.accountId()), viewer.kind(), body, now));
        thread.replied(now);
        return detail(viewer, lmsCourseId, threadId);
    }

    /** Pins or unpins a thread; only the course's faculty. */
    @Transactional
    public ThreadDetail pin(Viewer viewer, Long lmsCourseId, Long threadId, boolean pinned) {
        if (viewer.kind() != AuthorKind.FACULTY) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the course's faculty can pin discussions.");
        }
        thread(viewer, lmsCourseId, threadId);
        locked(threadId).pin(pinned);
        return detail(viewer, lmsCourseId, threadId);
    }

    /** Marks one reply of the thread as the accepted answer, replacing an earlier choice. */
    @Transactional
    public ThreadDetail accept(Viewer viewer, Long lmsCourseId, Long threadId, Long replyId) {
        LmsDiscussionThread found = thread(viewer, lmsCourseId, threadId);
        boolean asker = viewer.kind() == AuthorKind.STUDENT && found.getAuthorKind() == AuthorKind.STUDENT
                && found.getAuthor().getId().equals(viewer.accountId());
        if (viewer.kind() != AuthorKind.FACULTY && !asker) {
            throw new ApiException(HttpStatus.FORBIDDEN, NOT_YOURS);
        }
        LmsDiscussionThread thread = locked(threadId);
        LmsDiscussionReply reply = replyId == null ? null : replies.findById(replyId).orElse(null);
        if (reply == null || !reply.getThread().getId().equals(threadId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, REPLY_UNAVAILABLE);
        }
        thread.accept(reply.getId());
        return detail(viewer, lmsCourseId, threadId);
    }

    private LmsDiscussionThread thread(Viewer viewer, Long lmsCourseId, Long threadId) {
        requireCourse(viewer, lmsCourseId);
        return (threadId == null ? java.util.Optional.<LmsDiscussionThread>empty() : threads.findById(threadId))
                .filter(thread -> thread.getLmsCourse().getId().equals(lmsCourseId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, THREAD_UNAVAILABLE));
    }

    private LmsDiscussionThread locked(Long threadId) {
        return threads.findByIdForUpdate(threadId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, THREAD_UNAVAILABLE));
    }

    private void requireCourse(Viewer viewer, Long lmsCourseId) {
        participation.require(viewer, lmsCourseId);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
