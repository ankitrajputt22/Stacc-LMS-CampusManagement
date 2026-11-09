package com.stacc.backend.support.helpdesk;

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
import org.hibernate.annotations.CreationTimestamp;

/**
 * One entry of a ticket's audit trail: who did what, with the value before and after as short text, such as OPEN to
 * IN_PROGRESS or an assignee's login ID. Entries are append-only and never changed or deleted.
 */
@Entity
@Table(name = "helpdesk_ticket_history")
public class HelpdeskTicketHistory {

    public static final int MAX_VALUE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private HelpdeskTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_account_id", nullable = false, updatable = false)
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private HelpdeskTicketHistoryAction action;

    @Column(name = "from_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String fromValue;

    @Column(name = "to_value", updatable = false, length = MAX_VALUE_LENGTH)
    private String toValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HelpdeskTicketHistory() {
        // Required by JPA.
    }

    public HelpdeskTicketHistory(HelpdeskTicket ticket, UserAccount actor, HelpdeskTicketHistoryAction action,
            String fromValue, String toValue) {
        this.ticket = Require.notNull(ticket, "ticket");
        this.actor = Require.notNull(actor, "actor");
        this.action = Require.notNull(action, "action");
        this.fromValue = Require.optionalText(fromValue, "fromValue", MAX_VALUE_LENGTH);
        this.toValue = Require.optionalText(toValue, "toValue", MAX_VALUE_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public HelpdeskTicket getTicket() {
        return ticket;
    }

    public UserAccount getActor() {
        return actor;
    }

    public HelpdeskTicketHistoryAction getAction() {
        return action;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
