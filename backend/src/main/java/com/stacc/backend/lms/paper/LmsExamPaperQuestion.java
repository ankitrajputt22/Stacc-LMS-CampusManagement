package com.stacc.backend.lms.paper;

import java.math.BigDecimal;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.question.LmsQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One bank question in a paper, at a position, under an optional section title, with its marks in this paper. */
@Entity
@Table(name = "lms_exam_paper_questions")
public class LmsExamPaperQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: neither the paper nor the bank question is created or deleted through this link.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_exam_paper_id", nullable = false, updatable = false)
    private LmsExamPaper paper;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lms_question_id", nullable = false, updatable = false)
    private LmsQuestion question;

    @Column(nullable = false)
    private int position;

    @Column(name = "section_title", length = LmsExamBlueprintRow.MAX_SECTION_LENGTH)
    private String sectionTitle;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal marks;

    protected LmsExamPaperQuestion() {
        // Required by JPA.
    }

    public LmsExamPaperQuestion(LmsExamPaper paper, LmsQuestion question, int position, String sectionTitle, BigDecimal marks) {
        this.paper = Require.notNull(paper, "paper");
        this.question = Require.notNull(question, "question");
        this.position = Require.range(position, "position", 1, LmsExamPaper.MAX_QUESTIONS);
        this.sectionTitle = Require.optionalText(sectionTitle, "Section", LmsExamBlueprintRow.MAX_SECTION_LENGTH);
        this.marks = LmsExamBlueprintRow.marks(marks);
    }

    public Long getId() {
        return id;
    }

    public LmsExamPaper getPaper() {
        return paper;
    }

    public LmsQuestion getQuestion() {
        return question;
    }

    public int getPosition() {
        return position;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public BigDecimal getMarks() {
        return marks;
    }
}
