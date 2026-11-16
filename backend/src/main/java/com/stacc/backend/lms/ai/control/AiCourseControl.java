package com.stacc.backend.lms.ai.control;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A course's switch for the student AI Study Assistant (v0.13.7). No row means it is on. */
@Entity
@Table(name = "ai_course_controls")
public class AiCourseControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "student_ai_enabled", nullable = false)
    private boolean studentAiEnabled;

    @Column(name = "updated_by_account_id", nullable = false)
    private Long updatedByAccountId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiCourseControl() {
        // Required by JPA.
    }

    public AiCourseControl(Long lmsCourseId) {
        this.lmsCourseId = lmsCourseId;
        this.studentAiEnabled = true;
    }

    public boolean isStudentAiEnabled() {
        return studentAiEnabled;
    }

    public void change(boolean studentAiEnabled, Long accountId, Instant at) {
        this.studentAiEnabled = studentAiEnabled;
        this.updatedByAccountId = accountId;
        this.updatedAt = at;
    }
}
