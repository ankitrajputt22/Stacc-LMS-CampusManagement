package com.stacc.backend.lms.discussion;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A question or topic in a course's discussions. Its text never changes and it is never deleted; only its pin, lock,
 * hidden state, and accepted reply change, and its last activity moves with each reply. Faculty may hide it (students
 * then no longer see it) and lock it (it takes no new replies).
 */
@Entity
@Table(name = "lms_discussion_threads")
public class LmsDiscussionThread {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_account_id", nullable = false, updatable = false)
    private UserAccount author;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_kind", nullable = false, updatable = false, length = 20)
    private AuthorKind authorKind;

    @Column(nullable = false, updatable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by_account_id")
    private Long hiddenByAccountId;

    @Column(name = "accepted_reply_id")
    private Long acceptedReplyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected LmsDiscussionThread() {
        // Required by JPA.
    }

    public LmsDiscussionThread(LmsCourse lmsCourse, UserAccount author, AuthorKind authorKind, String title, String body, Instant now) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.author = Require.notNull(author, "author");
        this.authorKind = Require.notNull(authorKind, "authorKind");
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.body = Require.text(body, "body", MAX_BODY_LENGTH);
        this.createdAt = Require.notNull(now, "now");
        this.lastActivityAt = now;
    }

    public void replied(Instant at) {
        this.lastActivityAt = at;
    }

    public void pin(boolean pinned) {
        this.pinned = pinned;
    }

    public void accept(Long replyId) {
        this.acceptedReplyId = replyId;
    }

    public void lock(boolean locked) {
        this.locked = locked;
    }

    /** Hides the thread from students; hiding again keeps the first time and moderator. */
    public void hide(Long byAccountId, Instant at) {
        if (hiddenAt == null) {
            this.hiddenAt = Require.notNull(at, "at");
            this.hiddenByAccountId = Require.notNull(byAccountId, "byAccountId");
        }
    }

    public void unhide() {
        this.hiddenAt = null;
        this.hiddenByAccountId = null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public boolean isLocked() {
        return locked;
    }

    public Long getId() { return id; }
    public LmsCourse getLmsCourse() { return lmsCourse; }
    public UserAccount getAuthor() { return author; }
    public AuthorKind getAuthorKind() { return authorKind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public Long getAcceptedReplyId() { return acceptedReplyId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
}
