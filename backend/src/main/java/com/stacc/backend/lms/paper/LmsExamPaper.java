package com.stacc.backend.lms.paper;

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

@Entity
@Table(name = "lms_exam_papers")
public class LmsExamPaper {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_INSTRUCTIONS_LENGTH = 5_000;
    public static final int MAX_DURATION_MINUTES = 1440;
    public static final int MAX_QUESTIONS = 200;

    public enum Status {
        DRAFT,
        FINAL,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(name = "lms_exam_blueprint_id", updatable = false)
    private Long blueprintId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsExamPaper() {
    }

    public LmsExamPaper(LmsCourse lmsCourse, Long createdByAccountId, Long blueprintId, String title, String instructions,
            Integer durationMinutes) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        this.blueprintId = blueprintId;
        edit(title, instructions, durationMinutes);
    }

    public void edit(String title, String instructions, Integer durationMinutes) {
        requireDraft();
        this.title = Require.text(title, "Title", MAX_TITLE_LENGTH);
        this.instructions = Require.optionalText(instructions, "Instructions", MAX_INSTRUCTIONS_LENGTH);
        this.durationMinutes = durationMinutes == null ? null
                : Require.range(durationMinutes, "Duration in minutes", 1, MAX_DURATION_MINUTES);
    }

    public void finalizeAt(Instant now) {
        if (status == Status.FINAL) {
            return;
        }
        requireDraft();
        this.status = Status.FINAL;
        this.finalizedAt = Require.notNull(now, "now");
    }

    public void archive() {
        this.status = Status.ARCHIVED;
    }

    public void requireDraft() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException(status == Status.ARCHIVED
                    ? "This paper is archived and can no longer be changed."
                    : "This paper is final, so it can no longer be changed.");
        }
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public Long getBlueprintId() {
        return blueprintId;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
