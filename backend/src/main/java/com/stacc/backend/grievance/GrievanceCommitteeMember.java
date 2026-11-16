package com.stacc.backend.grievance;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An account on a category's committee; ended, never deleted. */
@Entity
@Table(name = "grievance_committee_members")
public class GrievanceCommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private GrievanceCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected GrievanceCommitteeMember() {
    }

    public GrievanceCommitteeMember(GrievanceCategory category, UserAccount account, Instant addedAt) {
        this.category = category;
        this.account = account;
        this.addedAt = addedAt;
    }

    public void end(Instant at) {
        if (endedAt == null) {
            endedAt = at;
        }
    }

    public Long getId() { return id; }
    public GrievanceCategory getCategory() { return category; }
    public UserAccount getAccount() { return account; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getEndedAt() { return endedAt; }
}
