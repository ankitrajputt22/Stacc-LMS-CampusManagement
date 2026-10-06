package com.stacc.backend.identity.student;

import java.time.Instant;

import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
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
 * The academic side of a student's account: which programme the student belongs to and the
 * academic session in which they were admitted. The student's ID is the account's login ID,
 * and the department comes from the programme, so neither is stored here again.
 *
 * <p>The profile holds no current semester. Which semester a student is in comes from
 * enrollment records. A profile that is no longer in use is made inactive rather than deleted.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    // No cascade on any link: the account, programme, and session are never created or
    // deleted through a student profile.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true, updatable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    // The session of original admission. It is history, not the student's current session.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_session_id", nullable = false, updatable = false)
    private AcademicSession admissionSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentProfileStatus status = StudentProfileStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentProfile() {
        // Required by JPA.
    }

    /**
     * The account should be one with the STUDENT role. That is checked by the feature that
     * creates student profiles, not here.
     */
    public StudentProfile(UserAccount userAccount, Program program, AcademicSession admissionSession) {
        this.userAccount = Require.notNull(userAccount, "userAccount");
        this.program = Require.notNull(program, "program");
        this.admissionSession = Require.notNull(admissionSession, "admissionSession");
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Program getProgram() {
        return program;
    }

    public AcademicSession getAdmissionSession() {
        return admissionSession;
    }

    public StudentProfileStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void activate() {
        this.status = StudentProfileStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = StudentProfileStatus.INACTIVE;
    }
}
