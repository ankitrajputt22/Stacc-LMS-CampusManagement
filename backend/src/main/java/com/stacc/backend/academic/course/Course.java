package com.stacc.backend.academic.course;

import java.math.BigDecimal;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A permanent entry in the college's course catalog, such as a subject with its code and
 * credits. It is not tied to a programme, a semester, or a session: a course being taught in
 * a particular semester is a separate record. A course that is no longer offered is made
 * inactive rather than deleted.
 */
@Entity
@Table(name = "courses")
public class Course {

    public static final int MAX_CODE_LENGTH = 30;
    public static final int MAX_NAME_LENGTH = 200;
    public static final BigDecimal MAX_CREDITS = new BigDecimal("20.00");

    private static final int CREDIT_DECIMAL_PLACES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The department responsible for the course. No cascade: it is never created or deleted through a course.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, precision = 4, scale = CREDIT_DECIMAL_PLACES)
    private BigDecimal credits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status = CourseStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
        // Required by JPA.
    }

    /**
     * The code is stored in uppercase without surrounding spaces and is unique across the college.
     * No particular code format is assumed.
     */
    public Course(Department department, String code, String name, BigDecimal credits) {
        this.department = Require.notNull(department, "department");
        this.code = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
        this.credits = validCredits(credits);
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

    /** The official credit value, always with two decimal places. */
    public BigDecimal getCredits() {
        return credits;
    }

    public CourseStatus getStatus() {
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

    public void changeCredits(BigDecimal credits) {
        this.credits = validCredits(credits);
    }

    public void activate() {
        this.status = CourseStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CourseStatus.INACTIVE;
    }

    // Credits may be fractional, such as 1.50. A value with more decimal places than can be
    // stored is rejected, never rounded.
    private static BigDecimal validCredits(BigDecimal credits) {
        Require.notNull(credits, "credits");
        if (credits.signum() <= 0) {
            throw new IllegalArgumentException("credits must be greater than zero");
        }
        if (credits.compareTo(MAX_CREDITS) > 0) {
            throw new IllegalArgumentException("credits must be at most " + MAX_CREDITS);
        }
        if (credits.stripTrailingZeros().scale() > CREDIT_DECIMAL_PLACES) {
            throw new IllegalArgumentException(
                    "credits must have at most " + CREDIT_DECIMAL_PLACES + " decimal places");
        }
        return credits.setScale(CREDIT_DECIMAL_PLACES);
    }
}
