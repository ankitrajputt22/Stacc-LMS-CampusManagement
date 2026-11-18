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

@Entity
@Table(name = "courses")
public class Course {

    public static final int MAX_CODE_LENGTH = 30;
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_SHORT_NAME_LENGTH = 20;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final BigDecimal MAX_CREDITS = new BigDecimal("20.00");

    private static final int CREDIT_DECIMAL_PLACES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "short_name", length = MAX_SHORT_NAME_LENGTH)
    private String shortName;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false, precision = 4, scale = CREDIT_DECIMAL_PLACES)
    private BigDecimal credits;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType = CourseType.CORE;

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
    }

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

    public BigDecimal getCredits() {
        return credits;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }

    public CourseType getCourseType() {
        return courseType;
    }

    public void describe(String shortName, String description, CourseType courseType) {
        this.shortName = Require.optionalText(shortName, "shortName", MAX_SHORT_NAME_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
        this.courseType = Require.notNull(courseType, "courseType");
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
