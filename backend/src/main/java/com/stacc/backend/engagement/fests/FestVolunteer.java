package com.stacc.backend.engagement.fests;

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

/** A person's application for a volunteer shift. Applied, then accepted or declined; withdrawn by them. Kept. */
@Entity
@Table(name = "campus_fest_volunteers")
public class FestVolunteer {

    public enum Status { APPLIED, ACCEPTED, DECLINED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false, updatable = false)
    private FestShift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Column(length = 500, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.APPLIED;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected FestVolunteer() {
        // Required by JPA.
    }

    public FestVolunteer(FestShift shift, UserAccount account, String note, Instant now) {
        this.shift = Require.notNull(shift, "shift");
        this.account = Require.notNull(account, "account");
        this.note = Require.optionalText(note, "Note", 500);
        this.appliedAt = Require.notNull(now, "now");
    }

    public void decide(boolean accept, Instant at) {
        if (status != Status.APPLIED) {
            throw new IllegalStateException("Only an open application can be accepted or declined.");
        }
        status = accept ? Status.ACCEPTED : Status.DECLINED;
        decidedAt = Require.notNull(at, "at");
    }

    public void withdraw() {
        if (status != Status.APPLIED && status != Status.ACCEPTED) {
            throw new IllegalStateException("Only an open or accepted application can be withdrawn.");
        }
        status = Status.WITHDRAWN;
    }

    public Long getId() { return id; }
    public FestShift getShift() { return shift; }
    public UserAccount getAccount() { return account; }
    public String getNote() { return note; }
    public Status getStatus() { return status; }
    public Instant getAppliedAt() { return appliedAt; }
}
