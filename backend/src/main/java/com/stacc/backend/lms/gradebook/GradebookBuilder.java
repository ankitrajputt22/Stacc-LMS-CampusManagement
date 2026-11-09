package com.stacc.backend.lms.gradebook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.QuizType;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptStatus;
import com.stacc.backend.lms.quiz.attempt.QuizResultStatus;
import com.stacc.backend.lms.quiz.attempt.QuizScores;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import org.springframework.stereotype.Component;

/**
 * Builds a course's gradebook for some of its course enrollments, reading every mark from where it lives:
 *
 * <ul>
 * <li>a published assignment: the grade of each student's latest attempt (an ungraded latest attempt needs grading);</li>
 * <li>a published graded quiz: the best score among the student's fully marked attempts (a handed-in attempt waiting
 * for a teacher needs grading); for a student's own view only attempts whose result is released count;</li>
 * <li>a manual item: the marks entered for it.</li>
 * </ul>
 *
 * Columns come in that order: assignments by due date, then quizzes and manual items in the order they were made.
 * Nothing is stored or changed here.
 */
@Component
public class GradebookBuilder {

    /** Whether a cell has marks yet. */
    public enum CellStatus {
        GRADED,
        /** Handed in but not marked yet. */
        NEEDS_GRADING,
        /** Nothing handed in, or no marks entered. */
        NO_MARKS
    }

    /** One column: where its marks come from, its title and maximum, and the gradebook's own record, if any. */
    public record Column(GradeItemType type, Long sourceId, String title, BigDecimal maxMarks, Instant dueAt, LmsGradeItem item) {

        public boolean published() {
            return item != null && item.isPublished();
        }
    }

    /** One student's marks in one column. */
    public record Cell(BigDecimal marks, CellStatus status, String feedback) {

        static final Cell EMPTY = new Cell(null, CellStatus.NO_MARKS, null);
    }

    /** The columns and each course enrollment's cells, in column order. */
    public record Gradebook(List<Column> columns, Map<Long, List<Cell>> cells) {

        /** The marks of the graded cells, and the maximum of their columns, over the columns kept. */
        public Total total(Long courseEnrollmentId, Predicate<Column> keep) {
            BigDecimal marks = BigDecimal.ZERO.setScale(2);
            BigDecimal outOf = BigDecimal.ZERO.setScale(2);
            List<Cell> row = cells.getOrDefault(courseEnrollmentId, List.of());
            for (int index = 0; index < columns.size(); index++) {
                Cell cell = row.get(index);
                if (keep.test(columns.get(index)) && cell.status() == CellStatus.GRADED) {
                    marks = marks.add(cell.marks());
                    outOf = outOf.add(columns.get(index).maxMarks());
                }
            }
            return new Total(marks, outOf);
        }
    }

    public record Total(BigDecimal marks, BigDecimal outOf) {
    }

    private final LmsAssignmentRepository assignments;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsSubmissionGradeRepository grades;
    private final LmsQuizRepository quizzes;
    private final LmsQuizQuestionRepository quizQuestions;
    private final LmsQuizAttemptRepository attempts;
    private final QuizScores scores;
    private final LmsGradeItemRepository items;
    private final LmsGradeEntryRepository entries;

    public GradebookBuilder(LmsAssignmentRepository assignments, LmsAssignmentSubmissionRepository submissions,
            LmsSubmissionGradeRepository grades, LmsQuizRepository quizzes, LmsQuizQuestionRepository quizQuestions,
            LmsQuizAttemptRepository attempts, QuizScores scores, LmsGradeItemRepository items, LmsGradeEntryRepository entries) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.grades = grades;
        this.quizzes = quizzes;
        this.quizQuestions = quizQuestions;
        this.attempts = attempts;
        this.scores = scores;
        this.items = items;
        this.entries = entries;
    }

    /** The course's columns, published or not. */
    public List<Column> columns(Long lmsCourseId) {
        List<LmsGradeItem> stored = items.findAllByLmsCourseIdOrderByIdAsc(lmsCourseId);
        Map<Long, LmsGradeItem> byAssignment = new HashMap<>();
        Map<Long, LmsGradeItem> byQuiz = new HashMap<>();
        for (LmsGradeItem item : stored) {
            if (item.getType() == GradeItemType.ASSIGNMENT) {
                byAssignment.put(item.getAssignment().getId(), item);
            } else if (item.getType() == GradeItemType.QUIZ) {
                byQuiz.put(item.getQuiz().getId(), item);
            }
        }
        List<Column> columns = new ArrayList<>();
        for (LmsAssignment assignment : assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(lmsCourseId,
                List.of(LmsContentStatus.PUBLISHED))) {
            columns.add(new Column(GradeItemType.ASSIGNMENT, assignment.getId(), assignment.getTitle(), assignment.getMaxMarks(),
                    assignment.getDueAt(), byAssignment.get(assignment.getId())));
        }
        List<LmsQuiz> graded = quizzes.findAllByLmsCourseIdAndStatusOrderByIdDesc(lmsCourseId, LmsContentStatus.PUBLISHED).stream()
                .filter(quiz -> quiz.getType() == QuizType.GRADED)
                .sorted(Comparator.comparing(LmsQuiz::getId))
                .toList();
        Map<Long, BigDecimal> quizTotals = graded.isEmpty() ? Map.of()
                : quizQuestions.totalsFor(graded.stream().map(LmsQuiz::getId).toList()).stream()
                        .collect(Collectors.toMap(LmsQuizQuestionRepository.QuizTotals::getQuizId,
                                LmsQuizQuestionRepository.QuizTotals::getTotalMarks));
        for (LmsQuiz quiz : graded) {
            columns.add(new Column(GradeItemType.QUIZ, quiz.getId(), quiz.getTitle(),
                    quizTotals.getOrDefault(quiz.getId(), BigDecimal.ZERO.setScale(2)), quiz.getDueAt(), byQuiz.get(quiz.getId())));
        }
        for (LmsGradeItem item : stored) {
            if (item.getType() == GradeItemType.MANUAL) {
                columns.add(new Column(GradeItemType.MANUAL, item.getId(), item.getTitle(), item.getMaxMarks(), null, item));
            }
        }
        return columns;
    }

    /**
     * The marks of these course enrollments in these columns. For a student's own view ({@code releasedOnly}), quiz
     * attempts count only once their result is released.
     */
    public Gradebook build(List<Column> columns, Collection<CourseEnrollment> enrollments, boolean releasedOnly, Instant now) {
        List<Long> enrollmentIds = enrollments.stream().map(CourseEnrollment::getId).toList();
        Map<Long, List<Cell>> cells = new HashMap<>();
        if (enrollmentIds.isEmpty()) {
            return new Gradebook(columns, cells);
        }
        Map<String, Cell> found = new HashMap<>();
        assignmentCells(columns, enrollmentIds, found);
        quizCells(columns, enrollmentIds, releasedOnly, now, found);
        manualCells(columns, enrollmentIds, found);
        for (Long enrollmentId : enrollmentIds) {
            cells.put(enrollmentId, columns.stream()
                    .map(column -> found.getOrDefault(key(column, enrollmentId), Cell.EMPTY))
                    .toList());
        }
        return new Gradebook(columns, cells);
    }

    private void assignmentCells(List<Column> columns, List<Long> enrollmentIds, Map<String, Cell> found) {
        List<Long> assignmentIds = sources(columns, GradeItemType.ASSIGNMENT);
        if (assignmentIds.isEmpty()) {
            return;
        }
        for (LmsAssignmentSubmission submission : submissions.findAllSubmitted(assignmentIds, enrollmentIds)) {
            found.put(key(GradeItemType.ASSIGNMENT, submission.getAssignment().getId(), submission.getCourseEnrollment().getId()),
                    new Cell(null, CellStatus.NEEDS_GRADING, null));
        }
        for (LmsSubmissionGrade grade : grades.findLatestAttemptGrades(assignmentIds, enrollmentIds)) {
            LmsAssignmentSubmission submission = grade.getAttempt().getSubmission();
            found.put(key(GradeItemType.ASSIGNMENT, submission.getAssignment().getId(), submission.getCourseEnrollment().getId()),
                    new Cell(grade.getMarksAwarded(), CellStatus.GRADED, grade.getFeedback()));
        }
    }

    private void quizCells(List<Column> columns, List<Long> enrollmentIds, boolean releasedOnly, Instant now, Map<String, Cell> found) {
        List<Long> quizIds = sources(columns, GradeItemType.QUIZ);
        if (quizIds.isEmpty()) {
            return;
        }
        List<LmsQuizAttempt> handedIn = attempts.findAllByQuizIdInAndCourseEnrollmentIdIn(quizIds, enrollmentIds).stream()
                .filter(attempt -> attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .toList();
        Map<Long, QuizScoring.AttemptScore> scored = scores.of(handedIn);
        for (LmsQuizAttempt attempt : handedIn) {
            QuizScoring.AttemptScore score = scored.get(attempt.getId());
            String key = key(GradeItemType.QUIZ, attempt.getQuiz().getId(), attempt.getCourseEnrollment().getId());
            Cell current = found.get(key);
            boolean counts = releasedOnly ? QuizResultStatus.of(attempt, score, now) == QuizResultStatus.RELEASED : score.fullyMarked();
            if (counts) {
                if (current == null || current.status() != CellStatus.GRADED || score.score().compareTo(current.marks()) > 0) {
                    found.put(key, new Cell(score.score(), CellStatus.GRADED, null));
                }
            } else if (current == null) {
                found.put(key, new Cell(null, CellStatus.NEEDS_GRADING, null));
            }
        }
    }

    private void manualCells(List<Column> columns, List<Long> enrollmentIds, Map<String, Cell> found) {
        List<Long> itemIds = sources(columns, GradeItemType.MANUAL);
        if (itemIds.isEmpty()) {
            return;
        }
        Map<Long, Boolean> wanted = enrollmentIds.stream().collect(Collectors.toMap(Function.identity(), id -> true));
        for (LmsGradeEntry entry : entries.findAllByItemIdIn(itemIds)) {
            Long enrollmentId = entry.getCourseEnrollment().getId();
            if (wanted.containsKey(enrollmentId)) {
                found.put(key(GradeItemType.MANUAL, entry.getItem().getId(), enrollmentId),
                        new Cell(entry.getMarks(), CellStatus.GRADED, entry.getFeedback()));
            }
        }
    }

    private static List<Long> sources(List<Column> columns, GradeItemType type) {
        return columns.stream().filter(column -> column.type() == type).map(Column::sourceId).filter(Objects::nonNull).toList();
    }

    private static String key(Column column, Long enrollmentId) {
        return key(column.type(), column.sourceId(), enrollmentId);
    }

    private static String key(GradeItemType type, Long sourceId, Long enrollmentId) {
        return type + ":" + sourceId + ":" + enrollmentId;
    }
}
