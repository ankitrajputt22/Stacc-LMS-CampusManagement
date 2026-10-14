package com.stacc.backend.identity.faculty;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The academic side of a faculty member's account: their primary department. The employee ID is
 * the account's login ID, so it is not stored here again, and neither is the person's name.
 *
 * <p>The primary department is where the faculty member belongs, not a limit on what they teach.
 * Which course offerings someone teaches will come from teaching assignments, never from this
 * profile. A profile that is no longer in use is made inactive rather than deleted.
 */
@Entity
@Table(name = "faculty_profiles")
public class FacultyProfile {

    // No cascade on any link: the account and the department are never created or deleted
    // through a faculty profile.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true, updatable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacultyProfileStatus status = FacultyProfileStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FacultyProfile() {
        // Required by JPA.
    }

    /**
     * The account should be one with the FACULTY role. That is checked by the feature that
     * creates faculty profiles, not here.
     */
    public FacultyProfile(UserAccount userAccount, Department department) {
        this.userAccount = Require.notNull(userAccount, "userAccount");
        this.department = Require.notNull(department, "department");
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Department getDepartment() {
        return department;
    }

    public FacultyProfileStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void activate() {
        this.status = FacultyProfileStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = FacultyProfileStatus.INACTIVE;
    }
}
