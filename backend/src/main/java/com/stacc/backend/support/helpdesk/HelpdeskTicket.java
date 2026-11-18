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

@Entity
@Table(name = "helpdesk_tickets")
public class HelpdeskTicket {

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;
    public static final String NUMBER_FORMAT = "STC-%06d";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private HelpdeskTeam team;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "rating_comment", length = 1000)
    private String ratingComment;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HelpdeskTicket() {
    }

    public HelpdeskTicket(UserAccount requester, HelpdeskCategory category, String subject, String description,
            HelpdeskTicketPriority priority) {
        this.requester = Require.notNull(requester, "requester");
        this.category = Require.notNull(category, "category");
        this.subject = Require.text(subject, "subject", MAX_SUBJECT_LENGTH);
        this.description = Require.text(description, "description", MAX_DESCRIPTION_LENGTH);
        this.priority = Require.notNull(priority, "priority");
    }

    public static String numberFor(long id) {
        return NUMBER_FORMAT.formatted(id);
    }

    public void assignNumber() {
        if (ticketNumber == null) {
            this.ticketNumber = numberFor(Require.notNull(id, "id"));
        }
    }

    public boolean isClosed() {
        return status == HelpdeskTicketStatus.CLOSED;
    }

    public void changePriority(HelpdeskTicketPriority priority) {
        requireNotClosed();
        this.priority = Require.notNull(priority, "priority");
    }

    public void assignTo(UserAccount assignee) {
        requireNotClosed();
        this.assignedTo = assignee;
    }

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

    public static final java.time.Duration REOPEN_WINDOW = java.time.Duration.ofDays(7);

    public void routeTo(HelpdeskTeam team) {
        requireNotClosed();
        this.team = team;
    }

    public void responded(Instant at) {
        if (firstResponseAt == null) {
            this.firstResponseAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
        }
    }

    public boolean canReopenAt(Instant at) {
        return status == HelpdeskTicketStatus.RESOLVED && resolvedAt != null && !at.isAfter(resolvedAt.plus(REOPEN_WINDOW));
    }

    public void reopen(Instant at) {
        if (!canReopenAt(at)) {
            throw new IllegalStateException("This ticket can no longer be reopened");
        }
        this.status = HelpdeskTicketStatus.IN_PROGRESS;
        this.resolvedAt = null;
    }

    public void rate(int stars, String comment, Instant at) {
        if (status != HelpdeskTicketStatus.RESOLVED && status != HelpdeskTicketStatus.CLOSED) {
            throw new IllegalStateException("Only a resolved or closed ticket can be rated");
        }
        if (rating != null) {
            throw new IllegalStateException("This ticket has already been rated");
        }
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("A rating is 1 to 5");
        }
        this.rating = stars;
        this.ratingComment = Require.optionalText(comment, "comment", 1000);
        this.ratedAt = Require.notNull(at, "at").truncatedTo(ChronoUnit.MICROS);
    }

    public HelpdeskTeam getTeam() { return team; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public Integer getRating() { return rating; }
    public String getRatingComment() { return ratingComment; }
    public Instant getRatedAt() { return ratedAt; }

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
