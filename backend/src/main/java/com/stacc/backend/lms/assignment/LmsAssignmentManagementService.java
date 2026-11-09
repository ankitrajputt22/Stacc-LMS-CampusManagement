package com.stacc.backend.lms.assignment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.rubric.LmsRubric;
import com.stacc.backend.lms.assignment.rubric.LmsRubricRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change faculty make to the assignments of an LMS course. Before any change, the course is found
 * from stored data, never from the request, and {@link LmsFacultyAccessService} must allow the signed-in
 * account to use it. An assignment that does not exist and one in a course the caller does not teach get
 * the same 404. Nothing is deleted: assignments are archived.
 */
@Service
@Transactional
public class LmsAssignmentManagementService {

    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final String ASSIGNMENT_UNAVAILABLE = "Assignment not found or unavailable.";
    static final String ASSIGNMENT_ARCHIVED = "This assignment is archived and can no longer be changed.";
    static final String OUTCOMES_INVALID = "Choose active outcomes of this course.";
    static final String RUBRIC_INVALID = "Choose an active rubric of this course.";

    private final LmsCourseRepository lmsCourses;
    private final LmsAssignmentRepository assignments;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;
    private final LmsAssignmentNotifications notifications;
    private final LmsCourseOutcomeRepository outcomes;
    private final LmsRubricRepository rubrics;

    public LmsAssignmentManagementService(
            LmsCourseRepository lmsCourses,
            LmsAssignmentRepository assignments,
            LmsFacultyAccessService facultyAccess,
            Clock clock,
            LmsAssignmentNotifications notifications,
            LmsCourseOutcomeRepository outcomes,
            LmsRubricRepository rubrics) {
        this.lmsCourses = lmsCourses;
        this.assignments = assignments;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
        this.notifications = notifications;
        this.outcomes = outcomes;
        this.rubrics = rubrics;
    }

    /** Adds a draft assignment with plain-text instructions to the course. */
    public LmsAssignment create(
            Long userAccountId, Long lmsCourseId, String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        return create(userAccountId, lmsCourseId, title, instructions, null, dueAt, maxMarks);
    }

    /** Adds a draft assignment to the course, with its instructions' format (plain when none is sent). */
    public LmsAssignment create(Long userAccountId, Long lmsCourseId, String title, String instructions,
            LmsTextFormat format, Instant dueAt, BigDecimal maxMarks) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        LmsAssignment assignment = new LmsAssignment(lmsCourse, title, instructions, dueAt, maxMarks);
        assignment.changeInstructionsFormat(LmsTextFormat.orKeep(format, LmsTextFormat.PLAIN));
        return assignments.save(assignment);
    }

    /**
     * Changes the definition of a draft or published assignment, including its deadline. Publication time
     * is kept, and nobody is notified.
     */
    public LmsAssignment update(
            Long userAccountId, Long assignmentId, String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        return update(userAccountId, assignmentId, title, instructions, null, dueAt, maxMarks);
    }

    /** As above; without a format the instructions keep their current one. */
    public LmsAssignment update(Long userAccountId, Long assignmentId, String title, String instructions,
            LmsTextFormat format, Instant dueAt, BigDecimal maxMarks) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        if (assignment.getRubric() != null && maxMarks != null && assignment.getRubric().getTotalPoints().compareTo(maxMarks) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, LmsAssignment.RUBRIC_TOTAL_FIXED);
        }
        if (dueAt != null && (assignment.getAvailableFrom() != null && dueAt.isBefore(assignment.getAvailableFrom())
                || assignment.getAvailableUntil() != null && dueAt.isAfter(assignment.getAvailableUntil()))) {
            throw new ApiException(HttpStatus.CONFLICT, LmsAssignment.DUE_OUTSIDE_WINDOW);
        }
        assignment.edit(title, instructions, dueAt, maxMarks);
        assignment.changeInstructionsFormat(LmsTextFormat.orKeep(format, assignment.getInstructionsFormat()));
        return assignment;
    }

    /**
     * Publishes a draft now and tells the students who may use the course. Publishing a published assignment changes
     * nothing and tells nobody again.
     */
    public LmsAssignment publish(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        boolean firstPublication = assignment.getStatus() != LmsContentStatus.PUBLISHED;
        // The database keeps microseconds, so this answer shows the same moment as every later read.
        assignment.publish(clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (firstPublication) {
            notifications.published(assignment);
        }
        return assignment;
    }

    /** Archives the assignment and keeps it. Archiving again changes nothing. */
    public LmsAssignment archive(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = manageable(userAccountId, assignmentId);
        assignment.archive();
        return assignment;
    }

    // The course is taken from the stored assignment, never from the request.
    private LmsAssignment manageable(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = assignmentId == null ? null : assignments.findById(assignmentId).orElse(null);
        if (assignment == null || !facultyAccess.canAccessCourse(userAccountId, assignment.getLmsCourse().getId())) {
            throw notFound(ASSIGNMENT_UNAVAILABLE);
        }
        return assignment;
    }

    /**
     * Aligns the assignment with a Bloom level (or none) and outcomes of its own course. A newly added outcome must be
     * active; one already aligned may stay after it is archived. Anything else is refused (409).
     */
    public LmsAssignment align(Long userAccountId, Long assignmentId, BloomLevel bloomLevel, Collection<Long> outcomeIds) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        Set<Long> wanted = new HashSet<>(outcomeIds);
        Set<Long> current = assignment.getOutcomes().stream().map(LmsCourseOutcome::getId).collect(Collectors.toSet());
        List<LmsCourseOutcome> chosen = outcomes.findAllByIdIn(wanted);
        boolean valid = chosen.size() == wanted.size() && chosen.stream().allMatch(outcome ->
                outcome.getLmsCourse().getId().equals(assignment.getLmsCourse().getId())
                        && (outcome.isActive() || current.contains(outcome.getId())));
        if (!valid) {
            throw new ApiException(HttpStatus.CONFLICT, OUTCOMES_INVALID);
        }
        assignment.align(bloomLevel, chosen);
        return assignment;
    }

    /**
     * Grades the assignment with an active rubric of its course from now on, or without a rubric (null). The rubric's
     * total must equal the maximum marks (409). Grades already given keep their own scores.
     */
    public LmsAssignment useRubric(Long userAccountId, Long assignmentId, Long rubricId) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        LmsRubric rubric = null;
        if (rubricId != null) {
            rubric = rubrics.findById(rubricId).orElse(null);
            if (rubric == null || !rubric.isActive() || !rubric.getLmsCourse().getId().equals(assignment.getLmsCourse().getId())) {
                throw new ApiException(HttpStatus.CONFLICT, RUBRIC_INVALID);
            }
        }
        try {
            assignment.useRubric(rubric);
        } catch (IllegalStateException | IllegalArgumentException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
        return assignment;
    }

    /**
     * Sets when the assignment takes work: opening and closing times around the due date, whether late work is accepted,
     * and an attempt limit. Attempts already handed in stay; the rules apply to new ones.
     */
    public LmsAssignment changeSubmissionRules(Long userAccountId, Long assignmentId, Instant opens, Instant closes,
            boolean allowLate, Integer attempts) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        try {
            assignment.changeSubmissionRules(opens, closes, allowLate, attempts);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        return assignment;
    }

    private LmsAssignment changeable(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = manageable(userAccountId, assignmentId);
        if (assignment.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, ASSIGNMENT_ARCHIVED);
        }
        return assignment;
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
