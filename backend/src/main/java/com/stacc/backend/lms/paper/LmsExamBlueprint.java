package com.stacc.backend.lms.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.course.LmsCourse;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The plan of an exam paper for one LMS course: its rows in order, each saying how many bank questions of which kind a
 * section takes and the marks each carries. A paper built from it is an ordinary paper afterwards; changing the
 * blueprint changes no paper. Archived, never deleted.
 */
@Entity
@Table(name = "lms_exam_blueprints")
public class LmsExamBlueprint {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_ROWS = 30;
    public static final int MAX_QUESTIONS = 200;

    public enum Status {
        ACTIVE,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_course_id", nullable = false, updatable = false)
    private LmsCourse lmsCourse;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    // Small and always shown with the blueprint, so it is loaded with it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lms_exam_blueprint_rows", joinColumns = @JoinColumn(name = "lms_exam_blueprint_id"))
    @OrderBy("position")
    private List<LmsExamBlueprintRow> rows = new ArrayList<>();

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LmsExamBlueprint() {
        // Required by JPA.
    }

    public LmsExamBlueprint(LmsCourse lmsCourse, Long createdByAccountId, String title, List<LmsExamBlueprintRow> rows) {
        this.lmsCourse = Require.notNull(lmsCourse, "lmsCourse");
        this.createdByAccountId = Require.notNull(createdByAccountId, "createdByAccountId");
        edit(title, rows);
    }

    /** Replaces the title and every row together. An archived blueprint no longer changes. */
    public void edit(String title, List<LmsExamBlueprintRow> newRows) {
        if (status == Status.ARCHIVED) {
            throw new IllegalStateException("This blueprint is archived and can no longer be changed.");
        }
        Require.notNull(newRows, "Rows");
        if (newRows.isEmpty() || newRows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("A blueprint needs 1 to " + MAX_ROWS + " rows.");
        }
        if (newRows.stream().mapToInt(LmsExamBlueprintRow::getQuestionCount).sum() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("A blueprint can ask for at most " + MAX_QUESTIONS + " questions.");
        }
        this.title = Require.text(title, "Title", MAX_TITLE_LENGTH);
        this.rows.clear();
        for (int index = 0; index < newRows.size(); index++) {
            LmsExamBlueprintRow row = newRows.get(index);
            this.rows.add(new LmsExamBlueprintRow(index + 1, row.getSectionTitle(), row.getQuestionType(), row.getDifficulty(),
                    row.getBloomLevel(), row.getOutcomeId(), row.getTopic(), row.getQuestionCount(), row.getMarksEach()));
        }
    }

    /** Archives the blueprint; archiving again changes nothing. */
    public void archive() {
        this.status = Status.ARCHIVED;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public int questionCount() {
        return rows.stream().mapToInt(LmsExamBlueprintRow::getQuestionCount).sum();
    }

    public BigDecimal totalMarks() {
        return rows.stream().map(LmsExamBlueprintRow::totalMarks).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public LmsCourse getLmsCourse() {
        return lmsCourse;
    }

    public String getTitle() {
        return title;
    }

    public Status getStatus() {
        return status;
    }

    public List<LmsExamBlueprintRow> getRows() {
        return List.copyOf(rows);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
