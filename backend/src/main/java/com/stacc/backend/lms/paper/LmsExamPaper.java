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

/**
 * An exam paper of one LMS course: a title, plain-text instructions, a duration, and bank questions in order (see
 * {@link LmsExamPaperQuestion}). A draft changes freely; a final paper keeps its details and questions, with the first
 * finalization time kept; an archived paper is kept for history. Nothing deletes a paper or reopens it.
 */
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

    // Which blueprint it was built from, for the record only: the blueprint never changes the paper afterwards.
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
        // Required by JPA.
    }

    public LmsExamPaper(LmsCourse lmsCourse, Long createdByAccountId, Long blueprintId, String title, String instructions,
            Integer durationMinutes) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        this.blueprintId = blueprintId;
        edit(title, instructions, durationMinutes);
    }

    /** Changes the title, instructions, and duration of a draft. */
    public void edit(String title, String instructions, Integer durationMinutes) {
        requireDraft();
        this.title = Require.text(title, "Title", MAX_TITLE_LENGTH);
        this.instructions = Require.optionalText(instructions, "Instructions", MAX_INSTRUCTIONS_LENGTH);
        this.durationMinutes = durationMinutes == null ? null
                : Require.range(durationMinutes, "Duration in minutes", 1, MAX_DURATION_MINUTES);
    }

    /** Makes a draft final, once; finalizing a final paper again changes nothing. */
    public void finalizeAt(Instant now) {
        if (status == Status.FINAL) {
            return;
        }
        requireDraft();
        this.status = Status.FINAL;
        this.finalizedAt = Require.notNull(now, "now");
    }

    /** Archives a draft or final paper; archiving again changes nothing. */
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
