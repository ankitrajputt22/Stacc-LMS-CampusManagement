package com.stacc.backend.engagement.events;

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

/** A place at an event, or on its waitlist. Cancelling keeps the row; registering again makes a new one. */
@Entity
@Table(name = "campus_event_registrations")
public class CampusEventRegistration {

    public enum Status { REGISTERED, WAITLISTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private CampusEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected CampusEventRegistration() {
        // Required by JPA.
    }

    public CampusEventRegistration(CampusEvent event, UserAccount account, boolean waitlisted, Instant at) {
        this.event = event;
        this.account = account;
        this.status = waitlisted ? Status.WAITLISTED : Status.REGISTERED;
        this.registeredAt = at;
    }

    public void promote(Instant at) {
        if (status == Status.WAITLISTED) {
            status = Status.REGISTERED;
            promotedAt = at;
        }
    }

    public void cancel(Instant at) {
        if (status != Status.CANCELLED) {
            status = Status.CANCELLED;
            cancelledAt = at;
        }
    }

    public Long getId() { return id; }
    public CampusEvent getEvent() { return event; }
    public UserAccount getAccount() { return account; }
    public Status getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
}
