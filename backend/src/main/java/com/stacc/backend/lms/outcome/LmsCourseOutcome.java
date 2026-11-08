package com.stacc.backend.lms.outcome;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
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
 * One learning outcome of an LMS course, such as "CO2: Analyse the cost of common data structures". The number and code
 * are given by the server and never change; only the plain-text description can be corrected, until it is archived.
 */
@Entity
@Table(name = "lms_course_outcomes")
public class LmsCourseOutcome {

    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: the course is never created or deleted through its outcomes.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(name = "outcome_number", nullable = false, updatable = false)
    private int number;

    @Column(nullable = false, updatable = false, length = 10)
    private String code;

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LmsOutcomeStatus status = LmsOutcomeStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsCourseOutcome() {
        // Required by JPA.
    }

    /** The outcome numbered {@code number} in its course, with the code "CO" and that number. */
    public LmsCourseOutcome(LmsCourse lmsCourse, int number, String description) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.number = Require.range(number, "number", 1, 999);
        this.code = "CO" + number;
        describe(description);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public int getNumber() {
        return number;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public LmsOutcomeStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == LmsOutcomeStatus.ACTIVE;
    }

    /** Corrects the description. An archived outcome can no longer change. */
    public void describe(String description) {
        if (status == LmsOutcomeStatus.ARCHIVED) {
            throw new IllegalStateException("An archived outcome cannot be changed");
        }
        this.description = Require.text(description, "description", MAX_DESCRIPTION_LENGTH);
    }

    /** Archives the outcome; archiving again changes nothing, and nothing restores it. */
    public void archive() {
        this.status = LmsOutcomeStatus.ARCHIVED;
    }
}
