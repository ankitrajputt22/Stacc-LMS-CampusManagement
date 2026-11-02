package com.stacc.backend.academic.responsibility;

import java.time.Instant;

import com.stacc.backend.academic.department.Department;
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

/**
 * One term of an account as head of one department. A department has at most one active term (a unique generated
 * column in the database). A new head starts a new term, so the history of who headed a department stays; ending a
 * term keeps the row. The department and the account never change after creation.
 */
@Entity
@Table(name = "department_heads")
public class DepartmentHead {

    // No cascade: departments and accounts are never created or deleted through a term.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, updatable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, updatable = false)
    private UserAccount userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentHeadStatus status = DepartmentHeadStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected DepartmentHead() {
        // Required by JPA.
    }

    /** Starts an active term. Whether the account may head the department is checked by the service. */
    public DepartmentHead(Department department, UserAccount userAccount, Instant startedAt) {
        this.department = Require.notNull(department, "department");
        this.userAccount = Require.notNull(userAccount, "userAccount");
        this.startedAt = Require.notNull(startedAt, "startedAt");
    }

    public Long getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public DepartmentHeadStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == DepartmentHeadStatus.ACTIVE;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    /** Ends the term. Ending an ended term changes nothing, and nothing reopens one. */
    public void end(Instant at) {
        Require.notNull(at, "at");
        if (status == DepartmentHeadStatus.ACTIVE) {
            status = DepartmentHeadStatus.ENDED;
            endedAt = at.isBefore(startedAt) ? startedAt : at;
        }
    }
}
