package com.stacc.backend.lms.quiz.attempt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class QuizAttemptOrder {

    private QuizAttemptOrder() {
    }

    public static <T> List<T> questions(long attemptId, List<T> inQuizOrder, boolean shuffle) {
        return shuffled(inQuizOrder, shuffle, attemptId);
    }

    public static <T> List<T> options(long attemptId, long quizQuestionId, List<T> inQuestionOrder, boolean shuffle) {
        return shuffled(inQuestionOrder, shuffle, attemptId * 1_000_003L + quizQuestionId);
    }

    private static <T> List<T> shuffled(List<T> items, boolean shuffle, long seed) {
        List<T> copy = new ArrayList<>(items);
        if (shuffle) {
            Collections.shuffle(copy, new Random(seed));
        }
        return List.copyOf(copy);
    }
}
