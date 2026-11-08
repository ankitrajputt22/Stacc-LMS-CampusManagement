package com.stacc.backend.lms.gradebook;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.QuizType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The gradebook of a course, for the faculty who teach it: every current student's marks in every column, manual items
 * and their marks, and publishing a column's grades to students. Every action checks the faculty access policy; for a
 * manual item the course comes from the stored item.
 *
 * <p>Publishing locks the course row and then reads its grade items with a locking read, so two publications of the same
 * column make one record (a unique key backs this up). Entering marks locks the manual item first.
 */
@Service
public class LmsGradebookService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String ITEM_UNAVAILABLE = "Grade item not found or unavailable.";
    public static final String COLUMN_UNAVAILABLE = "Choose a published assignment, a published graded quiz, or a manual item of this course.";
    public static final String STUDENT_UNAVAILABLE = "This student is not currently enrolled in the course.";
    public static final String MARKS_ABOVE_MAXIMUM = "Some marks already entered are above the new maximum.";

    private final GradebookBuilder builder;
    private final LmsGradeItemRepository items;
    private final LmsGradeEntryRepository entries;
    private final LmsCourseRepository lmsCourses;
    private final LmsAssignmentRepository assignments;
    private final LmsQuizRepository quizzes;
    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;

    public LmsGradebookService(GradebookBuilder builder, LmsGradeItemRepository items, LmsGradeEntryRepository entries,
            LmsCourseRepository lmsCourses, LmsAssignmentRepository assignments, LmsQuizRepository quizzes,
            CourseEnrollmentRepository courseEnrollments, LmsFacultyAccessService facultyAccess, Clock clock) {
        this.builder = builder;
        this.items = items;
        this.entries = entries;
        this.lmsCourses = lmsCourses;
        this.assignments = assignments;
        this.quizzes = quizzes;
        this.courseEnrollments = courseEnrollments;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
    }

    /** The gradebook with its students, by login ID. */
    public record FacultyGradebook(GradebookBuilder.Gradebook gradebook, List<CourseEnrollment> students) {
    }

    @Transactional(readOnly = true)
    public FacultyGradebook gradebook(Long userAccountId, Long lmsCourseId) {
        LmsCourse course = course(userAccountId, lmsCourseId);
        List<CourseEnrollment> students = courseEnrollments.findAllCurrentWithAccountByCourseOfferingId(
                course.getCourseOffering().getId());
        return new FacultyGradebook(builder.build(builder.columns(lmsCourseId), students, false, now()), students);
    }

    @Transactional
    public LmsGradeItem addManualItem(Long userAccountId, Long lmsCourseId, String title, BigDecimal maxMarks) {
        LmsCourse course = course(userAccountId, lmsCourseId);
        return items.saveAndFlush(valid(() -> LmsGradeItem.manual(course, userAccountId, title, maxMarks)));
    }

    /** Renames a manual item or changes its maximum; the maximum cannot fall below marks already entered (409). */
    @Transactional
    public LmsGradeItem editManualItem(Long userAccountId, Long itemId, String title, BigDecimal maxMarks) {
        LmsGradeItem item = lockedManual(userAccountId, itemId);
        if (maxMarks != null && entries.findAllForUpdate(itemId).stream()
                .anyMatch(entry -> entry.getMarks().compareTo(maxMarks) > 0)) {
            throw new ApiException(HttpStatus.CONFLICT, MARKS_ABOVE_MAXIMUM);
        }
        valid(() -> {
            item.edit(title, maxMarks);
            return item;
        });
        return items.saveAndFlush(item);
    }

    /**
     * Shows a column's grades to students from now on: a published assignment, a published graded quiz, or a manual
     * item of the course. Publishing again changes nothing; nothing unpublishes.
     */
    @Transactional
    public LmsGradeItem publishColumn(Long userAccountId, Long lmsCourseId, GradeItemType type, Long sourceId) {
        course(userAccountId, lmsCourseId);
        lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        List<LmsGradeItem> current = items.findAllForUpdate(lmsCourseId);
        LmsGradeItem item = switch (type == null ? GradeItemType.MANUAL : type) {
            case ASSIGNMENT -> {
                LmsAssignment assignment = sourceId == null ? null : assignments.findById(sourceId).orElse(null);
                if (assignment == null || assignment.getStatus() != LmsContentStatus.PUBLISHED
                        || !assignment.getLmsCourse().getId().equals(lmsCourseId)) {
                    throw new ApiException(HttpStatus.CONFLICT, COLUMN_UNAVAILABLE);
                }
                yield current.stream().filter(stored -> stored.getType() == GradeItemType.ASSIGNMENT
                        && stored.getAssignment().getId().equals(sourceId)).findFirst()
                        .orElseGet(() -> LmsGradeItem.forAssignment(assignment, userAccountId));
            }
            case QUIZ -> {
                LmsQuiz quiz = sourceId == null ? null : quizzes.findById(sourceId).orElse(null);
                if (quiz == null || quiz.getStatus() != LmsContentStatus.PUBLISHED || quiz.getType() != QuizType.GRADED
                        || !quiz.getLmsCourse().getId().equals(lmsCourseId)) {
                    throw new ApiException(HttpStatus.CONFLICT, COLUMN_UNAVAILABLE);
                }
                yield current.stream().filter(stored -> stored.getType() == GradeItemType.QUIZ
                        && stored.getQuiz().getId().equals(sourceId)).findFirst()
                        .orElseGet(() -> LmsGradeItem.forQuiz(quiz, userAccountId));
            }
            case MANUAL -> current.stream().filter(stored -> stored.getType() == GradeItemType.MANUAL
                    && stored.getId().equals(sourceId)).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, COLUMN_UNAVAILABLE));
        };
        item.publish(now());
        return items.saveAndFlush(item);
    }

    /** Enters one current student's marks for a manual item, replacing earlier ones. */
    @Transactional
    public LmsGradeEntry enterMarks(Long userAccountId, Long itemId, Long courseEnrollmentId, BigDecimal marks, String feedback) {
        LmsGradeItem item = lockedManual(userAccountId, itemId);
        CourseEnrollment enrollment = courseEnrollmentId == null ? null
                : courseEnrollments.findAllCurrentByCourseOfferingId(item.getLmsCourse().getCourseOffering().getId()).stream()
                        .filter(current -> current.getId().equals(courseEnrollmentId)).findFirst().orElse(null);
        if (enrollment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, STUDENT_UNAVAILABLE);
        }
        LmsGradeEntry entry = entries.findForUpdate(itemId, courseEnrollmentId)
                .orElseGet(() -> new LmsGradeEntry(item, enrollment));
        valid(() -> {
            entry.grade(marks, feedback, userAccountId, now());
            return entry;
        });
        return entries.saveAndFlush(entry);
    }

    // Only the course ID is read first, so the locking read is the first to load the item.
    private LmsGradeItem lockedManual(Long userAccountId, Long itemId) {
        Long lmsCourseId = itemId == null ? null : items.findLmsCourseIdById(itemId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ITEM_UNAVAILABLE);
        }
        LmsGradeItem item = items.findByIdForUpdate(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ITEM_UNAVAILABLE));
        if (item.getType() != GradeItemType.MANUAL) {
            throw new ApiException(HttpStatus.NOT_FOUND, ITEM_UNAVAILABLE);
        }
        return item;
    }

    private LmsCourse course(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        return lmsCourses.findById(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }
}
