package com.stacc.backend.lms.quiz.attempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionOption;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;

public final class QuizScoring {

    private QuizScoring() {
    }

    public record QuestionScore(LmsQuizQuestion quizQuestion, LmsQuizAnswer answer, QuizAnswerOutcome outcome,
            BigDecimal automaticMarks, LmsQuizAnswerMark mark) {

        public BigDecimal marks() {
            return mark != null ? mark.getMarks() : automaticMarks;
        }
    }

    public record AttemptScore(List<QuestionScore> questions) {

        public boolean fullyMarked() {
            return questions.stream().allMatch(question -> question.marks() != null);
        }

        public BigDecimal automaticMarks() {
            return sum(questions.stream().filter(question -> question.mark() == null && question.automaticMarks() != null)
                    .map(QuestionScore::automaticMarks).toList());
        }

        public BigDecimal teacherMarks() {
            return sum(questions.stream().filter(question -> question.mark() != null).map(question -> question.mark().getMarks())
                    .toList());
        }

        public BigDecimal score() {
            if (!fullyMarked()) {
                return null;
            }
            BigDecimal total = automaticMarks().add(teacherMarks());
            return total.signum() < 0 ? BigDecimal.ZERO.setScale(2) : total;
        }

        public BigDecimal totalMarks() {
            return sum(questions.stream().map(question -> question.quizQuestion().getMarks()).toList());
        }

        public long count(QuizAnswerOutcome outcome) {
            return questions.stream().filter(question -> question.outcome() == outcome).count();
        }

        private static BigDecimal sum(List<BigDecimal> values) {
            return values.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }
    }

    public static AttemptScore score(LmsQuiz quiz, List<LmsQuizQuestion> questions, Map<Long, LmsQuizAnswer> answersByQuestion,
            Map<Long, LmsQuizAnswerMark> marksByAnswer) {
        return new AttemptScore(questions.stream().map(quizQuestion -> {
            LmsQuizAnswer answer = answersByQuestion.get(quizQuestion.getId());
            LmsQuizAnswerMark mark = answer == null || answer.getId() == null ? null : marksByAnswer.get(answer.getId());
            return question(quiz, quizQuestion, answer, mark);
        }).toList());
    }

    static QuestionScore question(LmsQuiz quiz, LmsQuizQuestion quizQuestion, LmsQuizAnswer answer, LmsQuizAnswerMark mark) {
        LmsQuestion question = quizQuestion.getQuestion();
        BigDecimal marks = quizQuestion.getMarks();
        if (!answered(answer)) {
            return new QuestionScore(quizQuestion, answer, QuizAnswerOutcome.UNANSWERED, BigDecimal.ZERO.setScale(2), mark);
        }
        if (question.getType() == QuestionType.DESCRIPTIVE) {
            return new QuestionScore(quizQuestion, answer, mark == null ? QuizAnswerOutcome.NEEDS_MARKING : QuizAnswerOutcome.MARKED,
                    null, mark);
        }
        if (correct(question, answer)) {
            return new QuestionScore(quizQuestion, answer, QuizAnswerOutcome.CORRECT, marks, mark);
        }
        BigDecimal penalty = marks.multiply(quiz.getNegativeMarkPercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new QuestionScore(quizQuestion, answer, QuizAnswerOutcome.INCORRECT, penalty.negate(), mark);
    }

    static boolean answered(LmsQuizAnswer answer) {
        return answer != null && (!answer.getSelectedPositions().isEmpty() || answer.getTrueFalseAnswer() != null
                || answer.getTextAnswer() != null && !answer.getTextAnswer().isBlank());
    }

    static boolean correct(LmsQuestion question, LmsQuizAnswer answer) {
        return switch (question.getType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> answer.getSelectedPositions().equals(correctPositions(question));
            case TRUE_FALSE -> question.getTrueFalseAnswer().equals(answer.getTrueFalseAnswer());
            case SHORT_ANSWER -> answer.getTextAnswer() != null && question.getOptions().stream()
                    .anyMatch(accepted -> normalized(accepted.getText()).equals(normalized(answer.getTextAnswer())));
            case DESCRIPTIVE -> false;
        };
    }

    public static Set<Integer> correctPositions(LmsQuestion question) {
        return question.getOptions().stream().filter(LmsQuestionOption::isCorrect).map(LmsQuestionOption::getPosition)
                .collect(Collectors.toSet());
    }

    static String normalized(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
