package com.stacc.backend.lms.content;

import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "lms_page_completions", uniqueConstraints = @UniqueConstraint(
        name = "uk_lms_page_completions_enrollment_topic", columnNames = {"course_enrollment_id", "lms_topic_id"}))
public class LmsPageCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_enrollment_id", nullable = false, updatable = false)
    private CourseEnrollment courseEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_topic_id", nullable = false, updatable = false)
    private LmsTopic lmsTopic;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected LmsPageCompletion() {
    }

    public LmsPageCompletion(CourseEnrollment courseEnrollment, LmsTopic lmsTopic, Instant completedAt) {
        this.courseEnrollment = Require.notNull(courseEnrollment, "courseEnrollment");
        this.lmsTopic = Require.notNull(lmsTopic, "lmsTopic");
        this.completedAt = Require.notNull(completedAt, "completedAt");
    }

    public Long getId() {
        return id;
    }

    public LmsTopic getLmsTopic() {
        return lmsTopic;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
