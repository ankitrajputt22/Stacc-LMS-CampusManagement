package com.stacc.backend.lms.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One row of a blueprint: how many bank questions to take for a section, which ones may be taken (any filter left
 * empty matches every question), and the marks each carries in the paper.
 */
@Embeddable
public class LmsExamBlueprintRow {

    public static final int MAX_SECTION_LENGTH = 100;
    public static final int MAX_TOPIC_LENGTH = 100;
    public static final int MAX_COUNT = 100;
    public static final BigDecimal MAX_MARKS = new BigDecimal("999999.99");

    @Column(nullable = false)
    private int position;

    @Column(name = "section_title", length = MAX_SECTION_LENGTH)
    private String sectionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 20)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private QuestionDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", length = 20)
    private BloomLevel bloomLevel;

    @Column(name = "lms_course_outcome_id")
    private Long outcomeId;

    @Column(length = MAX_TOPIC_LENGTH)
    private String topic;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "marks_each", nullable = false, precision = 8, scale = 2)
    private BigDecimal marksEach;

    protected LmsExamBlueprintRow() {
        // Required by JPA.
    }

    public LmsExamBlueprintRow(int position, String sectionTitle, QuestionType questionType, QuestionDifficulty difficulty,
            BloomLevel bloomLevel, Long outcomeId, String topic, int questionCount, BigDecimal marksEach) {
        this.position = position;
        this.sectionTitle = Require.optionalText(sectionTitle, "Section", MAX_SECTION_LENGTH);
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.bloomLevel = bloomLevel;
        this.outcomeId = outcomeId;
        this.topic = Require.optionalText(topic, "Topic", MAX_TOPIC_LENGTH);
        this.questionCount = Require.range(questionCount, "Number of questions", 1, MAX_COUNT);
        this.marksEach = marks(marksEach);
    }

    static BigDecimal marks(BigDecimal marks) {
        Require.notNull(marks, "Marks");
        if (marks.signum() <= 0 || marks.compareTo(MAX_MARKS) > 0 || marks.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Marks must be greater than zero and below 1,000,000, with at most 2 decimal places.");
        }
        return marks.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Whether a bank question may fill this row: every filter that is set must match. */
    public boolean matches(QuestionType type, QuestionDifficulty questionDifficulty, BloomLevel level, Long questionOutcomeId,
            String questionTopic) {
        return (questionType == null || questionType == type)
                && (difficulty == null || difficulty == questionDifficulty)
                && (bloomLevel == null || bloomLevel == level)
                && (outcomeId == null || outcomeId.equals(questionOutcomeId))
                && (topic == null || (questionTopic != null && topic.equalsIgnoreCase(questionTopic.trim())));
    }

    public BigDecimal totalMarks() {
        return marksEach.multiply(BigDecimal.valueOf(questionCount));
    }

    public int getPosition() {
        return position;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public BloomLevel getBloomLevel() {
        return bloomLevel;
    }

    public Long getOutcomeId() {
        return outcomeId;
    }

    public String getTopic() {
        return topic;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public BigDecimal getMarksEach() {
        return marksEach;
    }
}
