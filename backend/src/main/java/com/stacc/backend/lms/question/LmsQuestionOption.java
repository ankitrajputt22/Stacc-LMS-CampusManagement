package com.stacc.backend.lms.question;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One choice of a choice question, or one accepted answer of a short-answer question. Part of its question: options are
 * replaced together when the question is edited and never shown to students with their {@code correct} flag.
 */
@Embeddable
public class LmsQuestionOption {

    public static final int MAX_TEXT_LENGTH = 500;

    @Column(nullable = false)
    private int position;

    @Column(name = "option_text", nullable = false, length = MAX_TEXT_LENGTH)
    private String text;

    @Column(nullable = false)
    private boolean correct;

    protected LmsQuestionOption() {
        // Required by JPA.
    }

    LmsQuestionOption(int position, String text, boolean correct) {
        this.position = Require.range(position, "position", 1, LmsQuestion.MAX_OPTIONS);
        this.text = Require.text(text, "option", MAX_TEXT_LENGTH);
        this.correct = correct;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return correct;
    }
}
