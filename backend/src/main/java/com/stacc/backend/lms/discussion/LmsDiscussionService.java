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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course discussions. A student takes part in a course the student access policy allows, faculty in a course the
 * faculty access policy allows; the kind comes from the route, the account from the access token. Anyone taking part
 * starts threads and replies; faculty pin threads; the thread's author or the faculty accept one reply as the answer.
 * Nothing is edited or deleted. A course or thread the caller may not use answers 404.
 *
 * <p>Moderation (v0.8.3): faculty hide threads and replies (students no longer see a hidden thread, and see a hidden
 * reply only as hidden) and lock threads against new replies. Students report a post with a reason; faculty resolve a
 * post's open reports together, by hiding it or dismissing them. Every moderation change locks the thread row first.
 */
@Service
public class LmsDiscussionService {

    public static final String THREAD_UNAVAILABLE = "Discussion not found or unavailable.";
    public static final String REPLY_UNAVAILABLE = "That reply is not part of this discussion.";
    public static final String NOT_YOURS = "Only the person who asked or the course's faculty can accept an answer.";
    public static final String LOCKED = "This discussion is locked, so it takes no new replies.";
    public static final String HIDDEN_REPLY = "A hidden reply can't be the accepted answer.";
    public static final String OWN_POST = "You can't report your own post.";
    public static final String ALREADY_REPORTED = "You have already reported this post. The course's faculty will look at it.";
    public static final String REPORT_UNAVAILABLE = "Report not found or already resolved.";
    public static final String FACULTY_ONLY = "Only the course's faculty can moderate discussions.";

    public record ThreadSummary(LmsDiscussionThread thread, long replies) {
    }

    public record ThreadDetail(LmsDiscussionThread thread, List<LmsDiscussionReply> replies) {
    }

    private final LmsDiscussionThreadRepository threads;
    private final LmsDiscussionReplyRepository replies;
    private final LmsDiscussionReportRepository reports;
    private final LmsCourseRepository lmsCourses;
    private final UserAccountRepository accounts;
    private final LmsCourseParticipation participation;
    private final Clock clock;

    public LmsDiscussionService(LmsDiscussionThreadRepository threads, LmsDiscussionReplyRepository replies,
            LmsDiscussionReportRepository reports, LmsCourseRepository lmsCourses, UserAccountRepository accounts,
            LmsCourseParticipation participation, Clock clock) {
        this.threads = threads;
        this.replies = replies;
        this.reports = reports;
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
                .filter(thread -> viewer.kind() == AuthorKind.FACULTY || !thread.isHidden())
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
        if (thread.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, LOCKED);
        }
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
        if (reply.isHidden()) {
            throw new ApiException(HttpStatus.CONFLICT, HIDDEN_REPLY);
        }
        thread.accept(reply.getId());
        return detail(viewer, lmsCourseId, threadId);
    }

    // --- Moderation

    /** Hides a thread from students, or shows it again; only the course's faculty. */
    @Transactional
    public ThreadDetail hideThread(Viewer viewer, Long lmsCourseId, Long threadId, boolean hidden) {
        requireFaculty(viewer);
        thread(viewer, lmsCourseId, threadId);
        LmsDiscussionThread thread = locked(threadId);
        if (hidden) {
            thread.hide(viewer.accountId(), now());
            resolveReports(viewer, threadId, null, LmsDiscussionReport.Resolution.HIDDEN);
        } else {
            thread.unhide();
        }
        return detail(viewer, lmsCourseId, threadId);
    }

    /** Locks a thread against new replies, or opens it again; only the course's faculty. */
    @Transactional
    public ThreadDetail lockThread(Viewer viewer, Long lmsCourseId, Long threadId, boolean locked) {
        requireFaculty(viewer);
        thread(viewer, lmsCourseId, threadId);
        locked(threadId).lock(locked);
        return detail(viewer, lmsCourseId, threadId);
    }

    /** Hides a reply from students, or shows it again. A hidden reply stops being the accepted answer. */
    @Transactional
    public ThreadDetail hideReply(Viewer viewer, Long lmsCourseId, Long threadId, Long replyId, boolean hidden) {
        requireFaculty(viewer);
        thread(viewer, lmsCourseId, threadId);
        LmsDiscussionThread thread = locked(threadId);
        LmsDiscussionReply reply = replyOf(threadId, replyId);
        if (hidden) {
            reply.hide(viewer.accountId(), now());
            if (reply.getId().equals(thread.getAcceptedReplyId())) {
                thread.accept(null);
            }
            resolveReports(viewer, threadId, replyId, LmsDiscussionReport.Resolution.HIDDEN);
        } else {
            reply.unhide();
        }
        return detail(viewer, lmsCourseId, threadId);
    }

    /**
     * A student reports a thread (no reply) or one of its replies to the course's faculty. Hidden posts cannot be
     * reported (404), nor one's own (409), and a person has one open report per post (409).
     */
    @Transactional
    public LmsDiscussionReport report(Viewer viewer, Long lmsCourseId, Long threadId, Long replyId, String reason) {
        if (viewer.kind() != AuthorKind.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Students report posts; faculty hide them directly.");
        }
        thread(viewer, lmsCourseId, threadId);
        LmsDiscussionThread thread = locked(threadId);
        LmsDiscussionReply reply = replyId == null ? null : replyOf(threadId, replyId);
        if (reply != null && reply.isHidden()) {
            throw new ApiException(HttpStatus.NOT_FOUND, REPLY_UNAVAILABLE);
        }
        Long authorId = reply == null ? thread.getAuthor().getId() : reply.getAuthor().getId();
        if (authorId.equals(viewer.accountId())) {
            throw new ApiException(HttpStatus.CONFLICT, OWN_POST);
        }
        if (reports.existsOpen(threadId, replyId, viewer.accountId())) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_REPORTED);
        }
        try {
            return reports.saveAndFlush(valid(() -> new LmsDiscussionReport(thread, reply, viewer.accountId(), reason, now())));
        } catch (DataIntegrityViolationException duplicate) {
            // The unique open-report key caught a report made at the same moment.
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_REPORTED);
        }
    }

    /** The course's open reports, oldest first; only the course's faculty. */
    @Transactional(readOnly = true)
    public List<LmsDiscussionReport> openReports(Viewer viewer, Long lmsCourseId) {
        requireFaculty(viewer);
        requireCourse(viewer, lmsCourseId);
        return reports.findOpenForCourse(lmsCourseId);
    }

    /** Resolves every open report of the reported post: hides the post, or dismisses the reports. */
    @Transactional
    public void resolve(Viewer viewer, Long lmsCourseId, Long reportId, boolean hide) {
        requireFaculty(viewer);
        requireCourse(viewer, lmsCourseId);
        LmsDiscussionReport report = (reportId == null ? java.util.Optional.<LmsDiscussionReport>empty() : reports.findById(reportId))
                .filter(found -> found.getThread().getLmsCourse().getId().equals(lmsCourseId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REPORT_UNAVAILABLE));
        Long threadId = report.getThread().getId();
        Long replyId = report.getReply() == null ? null : report.getReply().getId();
        if (hide) {
            if (replyId == null) {
                hideThread(viewer, lmsCourseId, threadId, true);
            } else {
                hideReply(viewer, lmsCourseId, threadId, replyId, true);
            }
        } else {
            locked(threadId);
            if (resolveReports(viewer, threadId, replyId, LmsDiscussionReport.Resolution.DISMISSED) == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, REPORT_UNAVAILABLE);
            }
        }
    }

    private int resolveReports(Viewer viewer, Long threadId, Long replyId, LmsDiscussionReport.Resolution resolution) {
        List<LmsDiscussionReport> open = reports.findOpenForPostForUpdate(threadId, replyId);
        Instant now = now();
        open.forEach(report -> report.resolve(resolution, viewer.accountId(), now));
        return open.size();
    }

    private LmsDiscussionReply replyOf(Long threadId, Long replyId) {
        LmsDiscussionReply reply = replyId == null ? null : replies.findById(replyId).orElse(null);
        if (reply == null || !reply.getThread().getId().equals(threadId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, REPLY_UNAVAILABLE);
        }
        return reply;
    }

    private static void requireFaculty(Viewer viewer) {
        if (viewer.kind() != AuthorKind.FACULTY) {
            throw new ApiException(HttpStatus.FORBIDDEN, FACULTY_ONLY);
        }
    }

    private static <T> T valid(java.util.function.Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    // A student never sees a hidden thread: it answers the same 404 as a missing one.
    private LmsDiscussionThread thread(Viewer viewer, Long lmsCourseId, Long threadId) {
        requireCourse(viewer, lmsCourseId);
        return (threadId == null ? java.util.Optional.<LmsDiscussionThread>empty() : threads.findById(threadId))
                .filter(thread -> thread.getLmsCourse().getId().equals(lmsCourseId))
                .filter(thread -> viewer.kind() == AuthorKind.FACULTY || !thread.isHidden())
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
