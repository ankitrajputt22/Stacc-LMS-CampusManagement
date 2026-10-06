package com.stacc.backend.academic.program;

import java.time.Instant;
import java.util.Locale;

import com.stacc.backend.academic.department.Department;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An official academic programme offered by one department. A programme that stops
 * taking students is made inactive rather than deleted, so older records can still refer to it.
 */
@Entity
@Table(
        name = "programs",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_programs_department_code", columnNames = {"department_id", "code"}),
            @UniqueConstraint(name = "uk_programs_department_name", columnNames = {"department_id", "name"})
        })
public class Program {

    public static final int MAX_CODE_LENGTH = 30;
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_DURATION_SEMESTERS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: a department is shared data and is never created or deleted through a programme.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "duration_semesters", nullable = false)
    private int durationSemesters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgramStatus status = ProgramStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Program() {
        // Required by JPA.
    }

    /** The code is stored in uppercase without surrounding spaces and is unique within the department. */
    public Program(Department department, String code, String name, int durationSemesters) {
        this.department = Require.notNull(department, "department");
        this.code = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
        this.durationSemesters = Require.range(durationSemesters, "durationSemesters", 1, MAX_DURATION_SEMESTERS);
    }

    public Long getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /** The normal length of the programme, counted in semesters. */
    public int getDurationSemesters() {
        return durationSemesters;
    }

    public ProgramStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String name) {
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
    }

    public void changeDuration(int durationSemesters) {
        this.durationSemesters = Require.range(durationSemesters, "durationSemesters", 1, MAX_DURATION_SEMESTERS);
    }

    public void activate() {
        this.status = ProgramStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ProgramStatus.INACTIVE;
    }
}
