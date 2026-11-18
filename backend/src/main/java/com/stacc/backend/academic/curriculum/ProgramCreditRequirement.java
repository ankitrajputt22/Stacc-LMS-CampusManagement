package com.stacc.backend.academic.curriculum;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.program.Program;
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

@Entity
@Table(name = "program_credit_requirements")
public class ProgramCreditRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20, updatable = false)
    private CourseType courseType;

    @Column(name = "required_credits", nullable = false, precision = 6, scale = 2)
    private BigDecimal requiredCredits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProgramCreditRequirement() {
    }

    public ProgramCreditRequirement(Program program, CourseType courseType, BigDecimal requiredCredits, Instant at) {
        this.program = program;
        this.courseType = courseType;
        this.requiredCredits = requiredCredits;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public void change(BigDecimal requiredCredits, Instant at) {
        this.requiredCredits = requiredCredits;
        this.updatedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Program getProgram() {
        return program;
    }

    public CourseType getCourseType() {
        return courseType;
    }

    public BigDecimal getRequiredCredits() {
        return requiredCredits;
    }
}
