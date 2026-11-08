package com.stacc.backend.lms.quiz.attempt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The order an attempt shows its questions and choices in. With shuffling on, each attempt gets its own order, worked
 * out again the same way on every read from the attempt's ID (and the question's), so nothing is stored and a reload
 * shows the same order. Without shuffling, the quiz's own order is kept.
 */
public final class QuizAttemptOrder {

    private QuizAttemptOrder() {
    }

    /** The items in the attempt's order. */
    public static <T> List<T> questions(long attemptId, List<T> inQuizOrder, boolean shuffle) {
        return shuffled(inQuizOrder, shuffle, attemptId);
    }

    /** The choices of one question in the attempt's order. */
    public static <T> List<T> options(long attemptId, long quizQuestionId, List<T> inQuestionOrder, boolean shuffle) {
        return shuffled(inQuestionOrder, shuffle, attemptId * 1_000_003L + quizQuestionId);
    }

    private static <T> List<T> shuffled(List<T> items, boolean shuffle, long seed) {
        List<T> copy = new ArrayList<>(items);
        if (shuffle) {
            // java.util.Random's sequence is fixed by its specification, so the same seed always gives the same order.
            Collections.shuffle(copy, new Random(seed));
        }
        return List.copyOf(copy);
    }
}
