package com.stacc.backend.engagement.events;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.engagement.groups.CampusGroup;
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
 * A campus event: who organizes it (optionally for a group), when and where, who may come, how many, and when
 * registration is open. A draft is edited freely; a group event waits for its advisor's approval before it shows;
 * cancelling keeps it and its registrations. Never deleted.
 */
@Entity
@Table(name = "campus_events")
public class CampusEvent {

    public enum Status { DRAFT, PENDING_APPROVAL, PUBLISHED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", updatable = false)
    private CampusGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_user_account_id", nullable = false, updatable = false)
    private UserAccount organizer;

    @Column(length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    private Integer capacity;

    @Column(name = "registration_opens_at")
    private Instant registrationOpensAt;

    @Column(name = "registration_closes_at")
    private Instant registrationClosesAt;

    @Column(name = "for_students", nullable = false)
    private boolean forStudents;

    @Column(name = "for_faculty", nullable = false)
    private boolean forFaculty;

    @Column(name = "members_only", nullable = false)
    private boolean membersOnly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusEvent() {
        // Required by JPA.
    }

    public CampusEvent(UserAccount organizer, CampusGroup group) {
        this.organizer = Require.notNull(organizer, "organizer");
        this.group = group;
    }

    /** Sets the details; only while a draft. */
    public void edit(String title, String description, String venue, Instant startsAt, Instant endsAt, Integer capacity,
            Instant registrationOpensAt, Instant registrationClosesAt, boolean forStudents, boolean forFaculty, boolean membersOnly) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only a draft event can be edited");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("the event must end after it starts");
        }
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("the capacity must be at least 1");
        }
        if (registrationOpensAt != null && registrationClosesAt != null && !registrationClosesAt.isAfter(registrationOpensAt)) {
            throw new IllegalArgumentException("registration must close after it opens");
        }
        if (registrationClosesAt != null && registrationClosesAt.isAfter(endsAt)) {
            throw new IllegalArgumentException("registration must close by the end of the event");
        }
        if (!forStudents && !forFaculty) {
            throw new IllegalArgumentException("the event must be for students, faculty, or both");
        }
        if (membersOnly && group == null) {
            throw new IllegalArgumentException("only a group's event can be for members only");
        }
        this.title = Require.text(title, "title", 200);
        this.description = Require.optionalText(description, "description", 10_000);
        this.venue = Require.optionalText(venue, "venue", 200);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.registrationOpensAt = registrationOpensAt;
        this.registrationClosesAt = registrationClosesAt;
        this.forStudents = forStudents;
        this.forFaculty = forFaculty;
        this.membersOnly = membersOnly;
    }

    public void publish(Instant at) {
        status = Status.PUBLISHED;
        if (publishedAt == null) {
            publishedAt = at;
        }
    }

    public void awaitApproval(Long approvalRequestId) {
        status = Status.PENDING_APPROVAL;
        this.approvalRequestId = approvalRequestId;
    }

    /** Back to a draft after the approval was refused, so it can be corrected and sent again. */
    public void backToDraft() {
        status = Status.DRAFT;
    }

    public void cancel(Instant at) {
        status = Status.CANCELLED;
        if (cancelledAt == null) {
            cancelledAt = at;
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public CampusGroup getGroup() { return group; }
    public UserAccount getOrganizer() { return organizer; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Integer getCapacity() { return capacity; }
    public Instant getRegistrationOpensAt() { return registrationOpensAt; }
    public Instant getRegistrationClosesAt() { return registrationClosesAt; }
    public boolean isForStudents() { return forStudents; }
    public boolean isForFaculty() { return forFaculty; }
    public boolean isMembersOnly() { return membersOnly; }
    public Status getStatus() { return status; }
    public Long getApprovalRequestId() { return approvalRequestId; }
    public Instant getPublishedAt() { return publishedAt; }
}
