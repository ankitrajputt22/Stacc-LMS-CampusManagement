package com.stacc.backend.lms.assignment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
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

    private final LmsCourseRepository lmsCourses;
    private final LmsAssignmentRepository assignments;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;
    private final LmsAssignmentNotifications notifications;

    public LmsAssignmentManagementService(
            LmsCourseRepository lmsCourses,
            LmsAssignmentRepository assignments,
            LmsFacultyAccessService facultyAccess,
            Clock clock,
            LmsAssignmentNotifications notifications) {
        this.lmsCourses = lmsCourses;
        this.assignments = assignments;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
        this.notifications = notifications;
    }

    /** Adds a draft assignment to the course. */
    public LmsAssignment create(
            Long userAccountId, Long lmsCourseId, String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw notFound(COURSE_UNAVAILABLE);
        }
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId).orElseThrow(() -> notFound(COURSE_UNAVAILABLE));
        return assignments.save(new LmsAssignment(lmsCourse, title, instructions, dueAt, maxMarks));
    }

    /**
     * Changes the definition of a draft or published assignment, including its deadline. Publication time
     * is kept, and nobody is notified.
     */
    public LmsAssignment update(
            Long userAccountId, Long assignmentId, String title, String instructions, Instant dueAt, BigDecimal maxMarks) {
        LmsAssignment assignment = changeable(userAccountId, assignmentId);
        assignment.edit(title, instructions, dueAt, maxMarks);
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
