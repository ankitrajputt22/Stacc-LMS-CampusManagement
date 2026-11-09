package com.stacc.backend.analytics;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.attendance.api.StudentAttendanceQueryService;
import com.stacc.backend.academic.attendance.api.StudentAttendanceResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import com.stacc.backend.lms.quiz.attempt.QuizAnswerOutcome;
import com.stacc.backend.lms.quiz.attempt.QuizAttemptStatus;
import com.stacc.backend.lms.quiz.attempt.QuizScores;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own analytics and a taught course's class analytics, worked out live by the rules in
 * {@link AnalyticsRules}. Course access comes from the same services as the course pages: the student course list
 * and the faculty access policy. Nothing is stored; nothing lists or ranks individual students.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    public record StudentCourse(Long lmsCourseId, String courseCode, String courseName, long assignmentsDue, long submitted, long late,
            BigDecimal averageMarkPercent) {
    }

    public record StudentAttendance(String courseCode, int sessions, int present, BigDecimal presentPercent) {
    }

    public record StudentAnalytics(BigDecimal overallAttendancePercent, List<StudentAttendance> attendance, List<StudentCourse> courses,
            BigDecimal assignmentAveragePercent, BigDecimal resultAveragePercent, int publishedResults) {
    }

    public record SessionLine(Long sessionId, long present, long total, BigDecimal presentPercent) {
    }

    public record AssignmentLine(Long assignmentId, String title, Instant dueAt, long submitted, BigDecimal submittedPercent, long late,
            long graded, BigDecimal averagePercent) {
    }

    public record QuestionLine(int position, String question, long answered, BigDecimal correctPercent) {
    }

    public record QuizLine(Long quizId, String title, long handedIn, BigDecimal averagePercent, List<QuestionLine> questions) {
    }

    public record PageLine(Long pageId, String title, long completed, BigDecimal completedPercent) {
    }

    public record CourseAnalytics(Long lmsCourseId, long enrolled, boolean smallGroup, String note, BigDecimal attendancePercent,
            List<SessionLine> sessions, List<AssignmentLine> assignments, List<QuizLine> quizzes, List<PageLine> requiredPages) {
    }

    private final LmsStudentCourseQueryService studentCourses;
    private final StudentAttendanceQueryService attendance;
    private final AnalyticsQueries queries;
    private final LmsSubmissionGradeRepository grades;
    private final LmsFacultyAccessService facultyAccess;
    private final LmsCourseRepository lmsCourses;
    private final CourseEnrollmentRepository enrollments;
    private final LmsQuizRepository quizzes;
    private final LmsQuizAttemptRepository quizAttempts;
    private final QuizScores quizScores;
    private final Clock clock;

    public AnalyticsService(LmsStudentCourseQueryService studentCourses, StudentAttendanceQueryService attendance, AnalyticsQueries queries,
            LmsSubmissionGradeRepository grades, LmsFacultyAccessService facultyAccess, LmsCourseRepository lmsCourses,
            CourseEnrollmentRepository enrollments, LmsQuizRepository quizzes, LmsQuizAttemptRepository quizAttempts, QuizScores quizScores,
            Clock clock) {
        this.studentCourses = studentCourses;
        this.attendance = attendance;
        this.queries = queries;
        this.grades = grades;
        this.facultyAccess = facultyAccess;
        this.lmsCourses = lmsCourses;
        this.enrollments = enrollments;
        this.quizzes = quizzes;
        this.quizAttempts = quizAttempts;
        this.quizScores = quizScores;
        this.clock = clock;
    }

    /** The student's own figures: attendance, assignment work and marks per current course, and published results. */
    public StudentAnalytics forStudent(Long accountId) {
        StudentAttendanceResponse own = attendance.getMyAttendance(accountId);
        List<StudentAttendance> attendanceLines = own.courses().stream()
                .map(c -> new StudentAttendance(c.courseCode(), c.totalSessions(), c.presentCount(), AnalyticsRules.percent(c.presentCount(), c.totalSessions())))
                .toList();
        long sessions = own.courses().stream().mapToLong(c -> c.totalSessions()).sum();
        long present = own.courses().stream().mapToLong(c -> c.presentCount()).sum();

        Map<Long, LmsCourseSummaryResponse> courses = new LinkedHashMap<>();
        studentCourses.getAccessibleCourses(accountId).forEach(c -> courses.putIfAbsent(c.lmsCourseId(), c));
        List<StudentCourse> courseLines = new ArrayList<>();
        List<BigDecimal> allMarks = new ArrayList<>();
        if (!courses.isEmpty()) {
            Instant now = clock.instant();
            Map<Long, Long> due = queries.findPublishedDue(courses.keySet(), now).stream()
                    .collect(Collectors.groupingBy(a -> a.getLmsCourse().getId(), Collectors.counting()));
            List<LmsSubmissionAttempt> latest = queries.findLatestAttemptsOfStudent(accountId, courses.keySet());
            Map<Long, LmsSubmissionGrade> gradeByAttempt = gradesOf(latest);
            for (LmsCourseSummaryResponse course : courses.values()) {
                List<LmsSubmissionAttempt> mine = latest.stream()
                        .filter(a -> a.getSubmission().getAssignment().getLmsCourse().getId().equals(course.lmsCourseId())).toList();
                List<BigDecimal> marks = mine.stream().map(a -> gradeByAttempt.get(a.getId())).filter(g -> g != null)
                        .map(g -> AnalyticsRules.percentOf(g.getMarksAwarded(), g.getMaxMarksSnapshot())).toList();
                allMarks.addAll(marks);
                courseLines.add(new StudentCourse(course.lmsCourseId(), course.courseCode(), course.courseName(),
                        due.getOrDefault(course.lmsCourseId(), 0L), mine.size(), mine.stream().filter(LmsSubmissionAttempt::isLate).count(),
                        AnalyticsRules.mean(marks)));
            }
        }
        List<BigDecimal> results = queries.findPublishedResultMarks(accountId).stream()
                .map(row -> AnalyticsRules.percentOf((BigDecimal) row[0], (BigDecimal) row[1])).filter(v -> v != null).toList();
        return new StudentAnalytics(AnalyticsRules.percent(present, sessions), attendanceLines, courseLines, AnalyticsRules.mean(allMarks),
                AnalyticsRules.mean(results), results.size());
    }

    /** Class analytics of a course the faculty member can open; any other answers 404. */
    public CourseAnalytics forCourse(Long accountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Course not found or unavailable.");
        }
        LmsCourse course = lmsCourses.findById(lmsCourseId).orElseThrow();
        Long offeringId = course.getCourseOffering().getId();
        long enrolled = enrollments.findAllByCourseOfferingIdAndStatus(offeringId, CourseEnrollmentStatus.ENROLLED).size();
        boolean small = enrolled < AnalyticsRules.MIN_GROUP;

        Map<Long, long[]> bySession = new LinkedHashMap<>();
        for (AnalyticsQueries.StatusCount row : queries.countFinalizedAttendance(offeringId)) {
            long[] counts = bySession.computeIfAbsent(row.getSessionId(), id -> new long[2]);
            counts[1] += row.getTotal();
            if ("PRESENT".equals(row.getStatus())) {
                counts[0] += row.getTotal();
            }
        }
        List<SessionLine> sessions = bySession.entrySet().stream()
                .map(e -> new SessionLine(e.getKey(), e.getValue()[0], e.getValue()[1], AnalyticsRules.classPercent(e.getValue()[0], e.getValue()[1], e.getValue()[1])))
                .toList();
        long allPresent = bySession.values().stream().mapToLong(c -> c[0]).sum();
        long allRecords = bySession.values().stream().mapToLong(c -> c[1]).sum();

        List<LmsSubmissionAttempt> latest = queries.findLatestAttemptsOfCourse(lmsCourseId);
        Map<Long, LmsSubmissionGrade> gradeByAttempt = gradesOf(latest);
        List<AssignmentLine> assignments = queries.findPublishedOfCourse(lmsCourseId).stream().map(assignment -> {
            List<LmsSubmissionAttempt> of = latest.stream().filter(a -> a.getSubmission().getAssignment().getId().equals(assignment.getId())).toList();
            List<BigDecimal> marks = of.stream().map(a -> gradeByAttempt.get(a.getId())).filter(g -> g != null)
                    .map(g -> AnalyticsRules.percentOf(g.getMarksAwarded(), g.getMaxMarksSnapshot())).toList();
            return new AssignmentLine(assignment.getId(), assignment.getTitle(), assignment.getDueAt(), of.size(),
                    AnalyticsRules.classPercent(of.size(), enrolled, enrolled), of.stream().filter(LmsSubmissionAttempt::isLate).count(),
                    marks.size(), AnalyticsRules.classMean(marks));
        }).toList();

        List<QuizLine> quizLines = quizzes.findAllByLmsCourseIdAndStatusOrderByIdDesc(lmsCourseId, LmsContentStatus.PUBLISHED).stream()
                .map(this::quizLine).toList();

        List<PageLine> pages = queries.countRequiredPageCompletions(lmsCourseId).stream()
                .map(p -> new PageLine(p.getTopicId(), p.getTitle(), p.getCompletions(), AnalyticsRules.classPercent(p.getCompletions(), enrolled, enrolled)))
                .toList();

        return new CourseAnalytics(lmsCourseId, enrolled, small, small ? AnalyticsRules.SMALL_GROUP : null,
                AnalyticsRules.classPercent(allPresent, allRecords, enrolled), sessions, assignments, quizLines, pages);
    }

    private QuizLine quizLine(LmsQuiz quiz) {
        List<LmsQuizAttempt> handedIn = quizAttempts.findAllWithStudentByQuizId(quiz.getId()).stream()
                .filter(a -> a.getStatus() != QuizAttemptStatus.IN_PROGRESS).toList();
        Map<Long, QuizScoring.AttemptScore> scores = quizScores.of(handedIn);
        List<BigDecimal> totals = scores.values().stream().filter(QuizScoring.AttemptScore::fullyMarked)
                .map(s -> AnalyticsRules.percentOf(s.score(), s.totalMarks())).filter(v -> v != null).toList();
        Map<Long, List<QuizScoring.QuestionScore>> byQuestion = scores.values().stream().flatMap(s -> s.questions().stream())
                .collect(Collectors.groupingBy(q -> q.quizQuestion().getId(), LinkedHashMap::new, Collectors.toList()));
        List<QuestionLine> questions = new ArrayList<>();
        int position = 1;
        for (List<QuizScoring.QuestionScore> answers : byQuestion.values()) {
            long answered = answers.stream().filter(a -> a.outcome() != QuizAnswerOutcome.UNANSWERED).count();
            long correct = answers.stream().filter(a -> a.outcome() == QuizAnswerOutcome.CORRECT).count();
            String text = answers.get(0).quizQuestion().getQuestion().getText();
            questions.add(new QuestionLine(position++, text.length() > 120 ? text.substring(0, 117) + "…" : text, answered,
                    AnalyticsRules.classPercent(correct, answered, answered)));
        }
        return new QuizLine(quiz.getId(), quiz.getTitle(), handedIn.size(), AnalyticsRules.classMean(totals), questions);
    }

    private Map<Long, LmsSubmissionGrade> gradesOf(Collection<LmsSubmissionAttempt> attempts) {
        if (attempts.isEmpty()) {
            return Map.of();
        }
        return grades.findAllByAttemptIdIn(attempts.stream().map(LmsSubmissionAttempt::getId).toList()).stream()
                .collect(Collectors.toMap(g -> g.getAttempt().getId(), Function.identity()));
    }
}
