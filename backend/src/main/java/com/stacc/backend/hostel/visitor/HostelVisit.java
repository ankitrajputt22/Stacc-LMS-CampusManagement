package com.stacc.backend.hostel.visitor;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.HostelAllocation;
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
 * A visitor of a hostel resident on one day: registered ahead by the resident (EXPECTED until security records the
 * arrival) or recorded by security as a walk-in (ARRIVED at once). It moves forward only: arrived, then left; an
 * expected visit can be cancelled. Never deleted.
 */
@Entity
@Table(name = "hostel_visits")
public class HostelVisit {

    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_RELATION_LENGTH = 60;

    public enum Kind {
        PRE_REGISTERED,
        WALK_IN
    }

    public enum Status {
        EXPECTED,
        ARRIVED,
        LEFT,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false, updatable = false)
    private HostelAllocation allocation;

    @Column(name = "visitor_name", nullable = false, updatable = false, length = MAX_NAME_LENGTH)
    private String visitorName;

    @Column(nullable = false, updatable = false, length = MAX_RELATION_LENGTH)
    private String relation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 15)
    private Kind kind;

    @Column(name = "expected_on", nullable = false, updatable = false)
    private LocalDate expectedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_account_id")
    private UserAccount recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HostelVisit() {
        // Required by JPA.
    }

    private HostelVisit(HostelAllocation allocation, String visitorName, String relation, Kind kind, LocalDate expectedOn, Instant now) {
        this.allocation = Require.notNull(allocation, "allocation");
        this.visitorName = Require.text(visitorName, "Visitor's name", MAX_NAME_LENGTH);
        this.relation = Require.text(relation, "Relation", MAX_RELATION_LENGTH);
        this.kind = kind;
        this.expectedOn = Require.notNull(expectedOn, "Date");
        this.createdAt = Require.notNull(now, "now");
        this.status = Status.EXPECTED;
    }

    public static HostelVisit preRegistered(HostelAllocation allocation, String name, String relation, LocalDate on, Instant now) {
        return new HostelVisit(allocation, name, relation, Kind.PRE_REGISTERED, on, now);
    }

    public static HostelVisit walkIn(HostelAllocation allocation, String name, String relation, LocalDate today, UserAccount by, Instant now) {
        HostelVisit visit = new HostelVisit(allocation, name, relation, Kind.WALK_IN, today, now);
        visit.arrive(by, now);
        return visit;
    }

    public void arrive(UserAccount by, Instant at) {
        if (status != Status.EXPECTED) {
            throw new IllegalStateException("Only an expected visitor can arrive.");
        }
        status = Status.ARRIVED;
        arrivedAt = Require.notNull(at, "at");
        recordedBy = Require.notNull(by, "recordedBy");
    }

    public void leave(Instant at) {
        if (status != Status.ARRIVED) {
            throw new IllegalStateException("Only a visitor inside can leave.");
        }
        status = Status.LEFT;
        leftAt = Require.notNull(at, "at");
    }

    public void cancel() {
        if (status != Status.EXPECTED) {
            throw new IllegalStateException("Only an expected visit can be cancelled.");
        }
        status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public HostelAllocation getAllocation() { return allocation; }
    public String getVisitorName() { return visitorName; }
    public String getRelation() { return relation; }
    public Kind getKind() { return kind; }
    public LocalDate getExpectedOn() { return expectedOn; }
    public Status getStatus() { return status; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getLeftAt() { return leftAt; }
}
