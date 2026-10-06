package com.stacc.backend.academic.semester;

import java.time.Instant;

import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
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
 * One numbered semester of one programme during one academic session, for example
 * semester 3 of a programme in 2026-27. This is where a programme and a session meet.
 * A finished semester is closed rather than deleted.
 */
@Entity
@Table(
        name = "semesters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_semesters_program_session_number",
                columnNames = {"program_id", "academic_session_id", "semester_number"}))
public class Semester {

    // No cascade on either link: programmes and sessions are shared data and are never
    // created or deleted through a semester.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false, updatable = false)
    private AcademicSession academicSession;

    @Column(name = "semester_number", nullable = false, updatable = false)
    private int semesterNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SemesterStatus status = SemesterStatus.PLANNED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Semester() {
        // Required by JPA.
    }

    /**
     * The programme, session, and number say what this semester is, so they cannot be changed
     * afterwards. The number must fit within the programme's length at the time of creation.
     */
    public Semester(Program program, AcademicSession academicSession, int semesterNumber) {
        this.program = Require.notNull(program, "program");
        this.academicSession = Require.notNull(academicSession, "academicSession");
        Require.range(semesterNumber, "semesterNumber", 1, Program.MAX_DURATION_SEMESTERS);
        if (semesterNumber > program.getDurationSemesters()) {
            throw new IllegalArgumentException(
                    "semesterNumber must not be greater than the program's " + program.getDurationSemesters()
                            + " semesters");
        }
        this.semesterNumber = semesterNumber;
    }

    public Long getId() {
        return id;
    }

    public Program getProgram() {
        return program;
    }

    public AcademicSession getAcademicSession() {
        return academicSession;
    }

    public int getSemesterNumber() {
        return semesterNumber;
    }

    public SemesterStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markActive() {
        if (status == SemesterStatus.CLOSED) {
            throw new IllegalStateException("A closed semester cannot be made active again");
        }
        this.status = SemesterStatus.ACTIVE;
    }

    public void close() {
        this.status = SemesterStatus.CLOSED;
    }
}
