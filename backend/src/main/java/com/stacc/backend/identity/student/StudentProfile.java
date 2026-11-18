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

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    public static final int MAX_ROLL_NUMBER_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true, updatable = false)
    private UserAccount userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_session_id", nullable = false, updatable = false)
    private AcademicSession admissionSession;

    @Column(name = "roll_number", length = MAX_ROLL_NUMBER_LENGTH, unique = true)
    private String rollNumber;

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
    }

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

    public String getRollNumber() {
        return rollNumber;
    }

    public void assignRollNumber(String rollNumber) {
        String value = Require.optionalText(rollNumber, "rollNumber", MAX_ROLL_NUMBER_LENGTH);
        this.rollNumber = value == null ? null : value.toUpperCase(java.util.Locale.ROOT);
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
