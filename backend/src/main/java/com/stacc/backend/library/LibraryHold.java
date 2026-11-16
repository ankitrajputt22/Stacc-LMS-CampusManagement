package com.stacc.backend.library;

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

/** A place in a title's queue: active until fulfilled by a loan or cancelled. Queue positions are worked out on read. */
@Entity
@Table(name = "library_holds")
public class LibraryHold {

    public enum Status { ACTIVE, FULFILLED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false, updatable = false)
    private LibraryTitle title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected LibraryHold() {
    }

    public LibraryHold(LibraryTitle title, UserAccount account, Instant placedAt) {
        this.title = title;
        this.account = account;
        this.placedAt = placedAt;
    }

    void close(Status outcome, Instant at) {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("This hold is no longer active.");
        }
        status = outcome;
        closedAt = at;
    }

    public Long getId() { return id; }
    public LibraryTitle getTitle() { return title; }
    public UserAccount getAccount() { return account; }
    public Status getStatus() { return status; }
    public Instant getPlacedAt() { return placedAt; }
}
