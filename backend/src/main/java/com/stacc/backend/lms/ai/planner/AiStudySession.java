package com.stacc.backend.lms.ai.planner;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** A study session a student added to their To-Do list from a plan (v0.15.7). */
@Entity
@Table(name = "ai_study_sessions")
public class AiStudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lms_course_id", nullable = false, updatable = false)
    private Long lmsCourseId;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(name = "planned_on", nullable = false, updatable = false)
    private LocalDate plannedOn;

    @Column(nullable = false, length = 250, updatable = false)
    private String title;

    @Column(nullable = false, updatable = false)
    private int minutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiStudySession() {
        // Required by JPA.
    }

    public AiStudySession(Long lmsCourseId, Long userAccountId, LocalDate plannedOn, String title, int minutes) {
        this.lmsCourseId = lmsCourseId;
        this.userAccountId = userAccountId;
        this.plannedOn = plannedOn;
        this.title = title.length() > 250 ? title.substring(0, 250) : title;
        this.minutes = minutes;
    }

    public Long getId() {
        return id;
    }

    public Long getLmsCourseId() {
        return lmsCourseId;
    }

    public LocalDate getPlannedOn() {
        return plannedOn;
    }

    public String getTitle() {
        return title;
    }

    public int getMinutes() {
        return minutes;
    }
}
