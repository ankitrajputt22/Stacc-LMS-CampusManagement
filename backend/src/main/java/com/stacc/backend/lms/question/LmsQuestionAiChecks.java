package com.stacc.backend.lms.question;

import com.stacc.backend.lms.outcome.BloomLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class LmsQuestionAiChecks {

    @Column(name = "ai_duplicate_of_question_id", updatable = false)
    private Long duplicateOfQuestionId;

    @Column(name = "ai_in_previous_paper", nullable = false, updatable = false)
    private boolean inPreviousPaper;

    @Column(name = "ai_out_of_syllabus", nullable = false, updatable = false)
    private boolean outOfSyllabus;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_difficulty", length = 10, updatable = false)
    private QuestionDifficulty suggestedDifficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_bloom_level", length = 20, updatable = false)
    private BloomLevel suggestedBloomLevel;

    protected LmsQuestionAiChecks() {
    }

    public LmsQuestionAiChecks(Long duplicateOfQuestionId, boolean inPreviousPaper, boolean outOfSyllabus,
            QuestionDifficulty suggestedDifficulty, BloomLevel suggestedBloomLevel) {
        this.duplicateOfQuestionId = duplicateOfQuestionId;
        this.inPreviousPaper = inPreviousPaper && duplicateOfQuestionId != null;
        this.outOfSyllabus = outOfSyllabus;
        this.suggestedDifficulty = suggestedDifficulty;
        this.suggestedBloomLevel = suggestedBloomLevel;
    }

    public static LmsQuestionAiChecks none() {
        return new LmsQuestionAiChecks();
    }

    public Long getDuplicateOfQuestionId() {
        return duplicateOfQuestionId;
    }

    public boolean isInPreviousPaper() {
        return inPreviousPaper;
    }

    public boolean isOutOfSyllabus() {
        return outOfSyllabus;
    }

    public QuestionDifficulty getSuggestedDifficulty() {
        return suggestedDifficulty;
    }

    public BloomLevel getSuggestedBloomLevel() {
        return suggestedBloomLevel;
    }
}
