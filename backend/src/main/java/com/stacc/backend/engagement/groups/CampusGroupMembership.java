package com.stacc.backend.engagement.groups;

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

/** A person's membership of a group: asked, accepted, refused, or left. Kept as history; joining again is a new row. */
@Entity
@Table(name = "campus_group_memberships")
public class CampusGroupMembership {

    public enum Status { PENDING, ACTIVE, REJECTED, LEFT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private CampusGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected CampusGroupMembership() {
        // Required by JPA.
    }

    public CampusGroupMembership(CampusGroup group, UserAccount account, boolean accepted, Instant at) {
        this.group = group;
        this.account = account;
        this.requestedAt = at;
        this.status = accepted ? Status.ACTIVE : Status.PENDING;
        this.decidedAt = accepted ? at : null;
    }

    public void decide(boolean accept, Instant at) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only a request to join can be decided");
        }
        status = accept ? Status.ACTIVE : Status.REJECTED;
        decidedAt = at;
    }

    public void leave(Instant at) {
        if (status == Status.PENDING || status == Status.ACTIVE) {
            status = Status.LEFT;
            leftAt = at;
        }
    }

    public Long getId() { return id; }
    public CampusGroup getGroup() { return group; }
    public UserAccount getAccount() { return account; }
    public Status getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
}
