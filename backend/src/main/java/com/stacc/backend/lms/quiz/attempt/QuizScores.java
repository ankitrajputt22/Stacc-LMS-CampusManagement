package com.stacc.backend.lms.quiz.attempt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import org.springframework.stereotype.Component;

/**
 * Scores attempts with {@link QuizScoring}, loading the questions, answers, and teachers' marks they need in three
 * queries however many attempts there are. Callers check access first; this only reads.
 */
@Component
public class QuizScores {

    private final LmsQuizQuestionRepository quizQuestions;
    private final LmsQuizAnswerRepository answers;
    private final LmsQuizAnswerMarkRepository marks;

    public QuizScores(LmsQuizQuestionRepository quizQuestions, LmsQuizAnswerRepository answers, LmsQuizAnswerMarkRepository marks) {
        this.quizQuestions = quizQuestions;
        this.answers = answers;
        this.marks = marks;
    }

    /** Each attempt's score by attempt ID, with its questions in the quiz's order. */
    public Map<Long, QuizScoring.AttemptScore> of(Collection<LmsQuizAttempt> attempts) {
        if (attempts.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LmsQuizQuestion>> questionsByQuiz = quizQuestions
                .findAllForQuizzes(attempts.stream().map(attempt -> attempt.getQuiz().getId()).distinct().toList()).stream()
                .collect(Collectors.groupingBy(link -> link.getQuiz().getId()));
        List<LmsQuizAnswer> saved = answers.findAllByAttemptIdIn(attempts.stream().map(LmsQuizAttempt::getId).toList());
        Map<Long, LmsQuizAnswerMark> marksByAnswer = saved.isEmpty() ? Map.of()
                : marks.findAllByAnswerIdIn(saved.stream().map(LmsQuizAnswer::getId).toList()).stream()
                        .collect(Collectors.toMap(mark -> mark.getAnswer().getId(), Function.identity()));
        Map<Long, Map<Long, LmsQuizAnswer>> answersByAttempt = saved.stream().collect(Collectors.groupingBy(
                answer -> answer.getAttempt().getId(),
                Collectors.toMap(answer -> answer.getQuizQuestion().getId(), Function.identity())));
        return attempts.stream().collect(Collectors.toMap(LmsQuizAttempt::getId, attempt -> QuizScoring.score(attempt.getQuiz(),
                questionsByQuiz.getOrDefault(attempt.getQuiz().getId(), List.of()),
                answersByAttempt.getOrDefault(attempt.getId(), Map.of()), marksByAnswer)));
    }

    public QuizScoring.AttemptScore of(LmsQuizAttempt attempt) {
        return of(List.of(attempt)).get(attempt.getId());
    }
}
