package com.stacc.backend.grievance;

import java.time.Duration;
import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
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
 * A confidential grievance. It moves forward only: submitted, under review, resolved; the complainant then accepts the
 * resolution (closed) or appeals once (appealed), and the committee's appeal decision closes it. Never deleted.
 */
@Entity
@Table(name = "grievance_cases")
public class GrievanceCase {

    public enum Status { SUBMITTED, UNDER_REVIEW, RESOLVED, APPEALED, CLOSED }

    public static final int MAX_TEXT = 10_000;
    /** How long after a resolution the complainant may appeal. */
    public static final Duration APPEAL_WINDOW = Duration.ofDays(14);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", length = 20)
    private String caseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private GrievanceCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complainant_account_id", nullable = false, updatable = false)
    private UserAccount complainant;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "respond_by", nullable = false, updatable = false)
    private Instant respondBy;

    @Column(name = "review_started_at")
    private Instant reviewStartedAt;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "appeal_reason", columnDefinition = "TEXT")
    private String appealReason;

    @Column(name = "appealed_at")
    private Instant appealedAt;

    @Column(name = "appeal_respond_by")
    private Instant appealRespondBy;

    @Column(name = "appeal_decision", columnDefinition = "TEXT")
    private String appealDecision;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected GrievanceCase() {
    }

    public GrievanceCase(GrievanceCategory category, UserAccount complainant, String subject, String details, Instant now) {
        if (!category.isActive()) {
            throw new IllegalStateException("This category is not taking new grievances.");
        }
        this.category = category;
        this.complainant = complainant;
        this.subject = text(subject, "Enter a subject.", 200);
        this.details = text(details, "Describe the grievance.", MAX_TEXT);
        this.submittedAt = now;
        this.respondBy = now.plus(Duration.ofDays(category.getResponseDays()));
    }

    static String text(String value, String message, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }

    void number() {
        caseNumber = String.format("GRV-%06d", id);
    }

    /** The committee starts its review; again changes nothing. */
    public boolean startReview(Instant now) {
        if (status == Status.UNDER_REVIEW) {
            return false;
        }
        require(Status.SUBMITTED, "Only a submitted grievance can be taken under review.");
        status = Status.UNDER_REVIEW;
        reviewStartedAt = now;
        return true;
    }

    public void resolve(String text, Instant now) {
        if (status == Status.SUBMITTED) {
            startReview(now);
        }
        require(Status.UNDER_REVIEW, "Only a grievance under review can be resolved.");
        resolution = text(text, "Write the committee's resolution.", MAX_TEXT);
        resolvedAt = now;
        status = Status.RESOLVED;
    }

    /** The complainant appeals once, within the appeal window after the resolution. */
    public void appeal(String reason, Instant now) {
        require(Status.RESOLVED, "Only a resolved grievance can be appealed, once.");
        if (!now.isBefore(resolvedAt.plus(APPEAL_WINDOW))) {
            throw new IllegalStateException("The time to appeal has passed.");
        }
        appealReason = text(reason, "Give the reason for the appeal.", MAX_TEXT);
        appealedAt = now;
        appealRespondBy = now.plus(Duration.ofDays(category.getResponseDays()));
        status = Status.APPEALED;
    }

    /** The complainant accepts the resolution, which closes the case. */
    public void accept(Instant now) {
        require(Status.RESOLVED, "Only a resolved grievance can be accepted.");
        status = Status.CLOSED;
        closedAt = now;
    }

    public void decideAppeal(String decision, Instant now) {
        require(Status.APPEALED, "Only an appealed grievance gets an appeal decision.");
        appealDecision = text(decision, "Write the appeal decision.", MAX_TEXT);
        status = Status.CLOSED;
        closedAt = now;
    }

    /** Past its respond-by time with no answer yet, worked out on read. */
    public boolean overdue(Instant now) {
        return status == Status.SUBMITTED && now.isAfter(respondBy) || status == Status.APPEALED && now.isAfter(appealRespondBy);
    }

    private void require(Status expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public Long getId() { return id; }
    public String getCaseNumber() { return caseNumber; }
    public GrievanceCategory getCategory() { return category; }
    public UserAccount getComplainant() { return complainant; }
    public String getSubject() { return subject; }
    public String getDetails() { return details; }
    public Status getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getRespondBy() { return respondBy; }
    public Instant getReviewStartedAt() { return reviewStartedAt; }
    public String getResolution() { return resolution; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getAppealReason() { return appealReason; }
    public Instant getAppealedAt() { return appealedAt; }
    public Instant getAppealRespondBy() { return appealRespondBy; }
    public String getAppealDecision() { return appealDecision; }
    public Instant getClosedAt() { return closedAt; }
}
