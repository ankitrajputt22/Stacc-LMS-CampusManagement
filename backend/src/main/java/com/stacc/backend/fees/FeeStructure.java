package com.stacc.backend.fees;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** What a program's students pay in one academic session, by fee head, with one due date. */
@Entity
@Table(name = "fee_structures")
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_session_id", nullable = false, updatable = false)
    private AcademicSession academicSession;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeeStructure() {
    }

    public FeeStructure(Program program, AcademicSession academicSession, LocalDate dueDate, Instant at) {
        this.program = program;
        this.academicSession = academicSession;
        this.dueDate = dueDate;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public void changeDueDate(LocalDate dueDate, Instant at) {
        this.dueDate = dueDate;
        this.updatedAt = at;
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

    public LocalDate getDueDate() {
        return dueDate;
    }
}
