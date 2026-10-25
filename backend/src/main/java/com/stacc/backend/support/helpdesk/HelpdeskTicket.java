package com.stacc.backend.support.helpdesk;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One support request: who raised it, its category, a plain-text subject and original description, its priority and
 * status, and the admin it is assigned to, if any. The requester never changes. A new ticket is OPEN and unassigned;
 * its status only moves forward ({@link HelpdeskTicketStatus#canMoveTo}), and once CLOSED nothing about it changes.
 * The conversation and the audit trail are kept in {@link HelpdeskTicketMessage} and {@link HelpdeskTicketHistory}.
 */
@Entity
@Table(name = "helpdesk_tickets")
public class HelpdeskTicket {

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;
    public static final String NUMBER_FORMAT = "STC-%06d";

    // No cascade on any link: accounts and categories are never created or deleted through a ticket.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Derived from the ID right after the first insert, in the same transaction, so every stored ticket has one.
    @Column(name = "ticket_number", unique = true, length = 20)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_account_id", nullable = false, updatable = false)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private HelpdeskCategory category;

    @Column(nullable = false, length = MAX_SUBJECT_LENGTH)
    private String subject;

    // Plain text. It is never treated as HTML.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelpdeskTicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelpdeskTicketStatus status = HelpdeskTicketStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_account_id")
    private UserAccount assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HelpdeskTicket() {
        // Required by JPA.
    }

    /** A new OPEN, unassigned ticket. Whether the category may be used and the priority chosen is checked by the caller. */
    public HelpdeskTicket(UserAccount requester, HelpdeskCategory category, String subject, String description,
            HelpdeskTicketPriority priority) {
        this.requester = Require.notNull(requester, "requester");
        this.category = Require.notNull(category, "category");
        this.subject = Require.text(subject, "subject", MAX_SUBJECT_LENGTH);
        this.description = Require.text(description, "description", MAX_DESCRIPTION_LENGTH);
        this.priority = Require.notNull(priority, "priority");
    }

    /** The human-readable number of a ticket with this ID, such as STC-000042. */
    public static String numberFor(long id) {
        return NUMBER_FORMAT.formatted(id);
    }

    /** Gives the saved ticket its number, once. */
    public void assignNumber() {
        if (ticketNumber == null) {
            this.ticketNumber = numberFor(Require.notNull(id, "id"));
        }
    }

    public boolean isClosed() {
        return status == HelpdeskTicketStatus.CLOSED;
    }

    /** Changes the priority of a ticket that is not closed. */
    public void changePriority(HelpdeskTicketPriority priority) {
        requireNotClosed();
        this.priority = Require.notNull(priority, "priority");
    }

    /** Assigns the ticket to an admin account, or unassigns it with null. Who may be assigned is checked by the caller. */
    public void assignTo(UserAccount assignee) {
        requireNotClosed();
        this.assignedTo = assignee;
    }

    /**
     * Moves the ticket to the target status. The first move to RESOLVED or CLOSED records when; the same status changes
     * nothing. A move the lifecycle does not allow, such as back to OPEN, is refused.
     */
    public void moveTo(HelpdeskTicketStatus target, Instant at) {
        Require.notNull(target, "status");
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("A " + status + " ticket cannot move to " + target);
        }
        if (target == status) {
            return;
        }
        Instant now = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        if (target == HelpdeskTicketStatus.RESOLVED && resolvedAt == null) {
            this.resolvedAt = now;
        }
        if (target == HelpdeskTicketStatus.CLOSED && closedAt == null) {
            this.closedAt = now;
        }
        this.status = target;
    }

    private void requireNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("A closed ticket cannot be changed");
        }
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public HelpdeskCategory getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public HelpdeskTicketPriority getPriority() {
        return priority;
    }

    public HelpdeskTicketStatus getStatus() {
        return status;
    }

    public UserAccount getAssignedTo() {
        return assignedTo;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
