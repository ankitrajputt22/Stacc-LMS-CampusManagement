package com.stacc.backend.lms.discussion;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
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

/** A reply in a discussion thread. Replies are never edited or deleted. */
@Entity
@Table(name = "lms_discussion_replies")
public class LmsDiscussionReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false, updatable = false)
    private LmsDiscussionThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_account_id", nullable = false, updatable = false)
    private UserAccount author;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_kind", nullable = false, updatable = false, length = 20)
    private AuthorKind authorKind;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LmsDiscussionReply() {
        // Required by JPA.
    }

    public LmsDiscussionReply(LmsDiscussionThread thread, UserAccount author, AuthorKind authorKind, String body, Instant now) {
        this.thread = Require.notNull(thread, "thread");
        this.author = Require.notNull(author, "author");
        this.authorKind = Require.notNull(authorKind, "authorKind");
        this.body = Require.text(body, "body", LmsDiscussionThread.MAX_BODY_LENGTH);
        this.createdAt = Require.notNull(now, "now");
    }

    public Long getId() { return id; }
    public LmsDiscussionThread getThread() { return thread; }
    public UserAccount getAuthor() { return author; }
    public AuthorKind getAuthorKind() { return authorKind; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
