package com.stacc.backend.support.helpdesk;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** One plain-text message in a ticket's conversation. Messages are append-only: never edited, never deleted. */
@Entity
@Table(name = "helpdesk_ticket_messages")
public class HelpdeskTicketMessage {

    public static final int MAX_MESSAGE_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private HelpdeskTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_account_id", nullable = false, updatable = false)
    private UserAccount author;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HelpdeskTicketMessage() {
        // Required by JPA.
    }

    public HelpdeskTicketMessage(HelpdeskTicket ticket, UserAccount author, String message) {
        this.ticket = Require.notNull(ticket, "ticket");
        this.author = Require.notNull(author, "author");
        this.message = Require.text(message, "message", MAX_MESSAGE_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public HelpdeskTicket getTicket() {
        return ticket;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
