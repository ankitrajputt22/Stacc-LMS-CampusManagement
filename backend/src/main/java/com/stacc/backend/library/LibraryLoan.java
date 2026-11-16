package com.stacc.backend.library;

import java.time.Duration;
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

/** A copy lent to a borrower until a due time, renewed at most once, and returned once. Kept as history. */
@Entity
@Table(name = "library_loans")
public class LibraryLoan {

    public static final Duration PERIOD = Duration.ofDays(14);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false, updatable = false)
    private LibraryCopy copy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_account_id", nullable = false, updatable = false)
    private UserAccount borrower;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private boolean renewed;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "issued_by_account_id", nullable = false, updatable = false)
    private Long issuedBy;

    protected LibraryLoan() {
    }

    public LibraryLoan(LibraryCopy copy, UserAccount borrower, Long issuedBy, Instant issuedAt) {
        this.copy = copy;
        this.borrower = borrower;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
        this.dueAt = issuedAt.plus(PERIOD);
    }

    /** Extends the due time once, from the current due time, unless already overdue. */
    public void renew(Instant now) {
        if (returnedAt != null || renewed) {
            throw new IllegalStateException("A loan is renewed at most once, before it is returned.");
        }
        if (now.isAfter(dueAt)) {
            throw new IllegalStateException("An overdue loan cannot be renewed; return it at the desk.");
        }
        renewed = true;
        dueAt = dueAt.plus(PERIOD);
    }

    public boolean giveBack(Instant at) {
        if (returnedAt != null) {
            return false;
        }
        returnedAt = at;
        return true;
    }

    public boolean overdue(Instant now) {
        return returnedAt == null && now.isAfter(dueAt);
    }

    public Long getId() { return id; }
    public LibraryCopy getCopy() { return copy; }
    public UserAccount getBorrower() { return borrower; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getDueAt() { return dueAt; }
    public boolean isRenewed() { return renewed; }
    public Instant getReturnedAt() { return returnedAt; }
}
