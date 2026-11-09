package com.stacc.backend.lms.message;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.discussion.AuthorKind;
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

/** One message in a conversation. Messages are never edited or deleted. */
@Entity
@Table(name = "lms_conversation_messages")
public class LmsConversationMessage {

    public static final int MAX_BODY_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private LmsConversation conversation;

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

    protected LmsConversationMessage() {
        // Required by JPA.
    }

    public LmsConversationMessage(LmsConversation conversation, UserAccount author, AuthorKind authorKind, String body, Instant now) {
        this.conversation = Require.notNull(conversation, "conversation");
        this.author = Require.notNull(author, "author");
        this.authorKind = Require.notNull(authorKind, "authorKind");
        this.body = Require.text(body, "body", MAX_BODY_LENGTH);
        this.createdAt = Require.notNull(now, "now");
    }

    public Long getId() { return id; }
    public LmsConversation getConversation() { return conversation; }
    public UserAccount getAuthor() { return author; }
    public AuthorKind getAuthorKind() { return authorKind; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
