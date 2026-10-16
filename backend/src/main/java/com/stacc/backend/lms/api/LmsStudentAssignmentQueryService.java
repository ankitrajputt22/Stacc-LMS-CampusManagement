package com.stacc.backend.lms.api;

import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only answers about the assignments of an LMS course, for students. A student sees an assignment
 * only when {@link LmsStudentAccessService} allows the course and the assignment is published. A published
 * assignment stays listed after its deadline.
 */
@Service
@Transactional(readOnly = true)
public class LmsStudentAssignmentQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";
    static final String ASSIGNMENT_UNAVAILABLE_MESSAGE = "Assignment not found or unavailable.";

    private final LmsStudentAccessService studentAccess;
    private final LmsAssignmentRepository assignments;

    public LmsStudentAssignmentQueryService(LmsStudentAccessService studentAccess, LmsAssignmentRepository assignments) {
        this.studentAccess = studentAccess;
        this.assignments = assignments;
    }

    /**
     * The published assignments of a course the student may use, earliest deadline first, past-due ones
     * included. A course the student may not use answers the same 404 as a course that does not exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsCourseAssignmentResponse> getPublishedAssignments(Long userAccountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
        }
        return assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY)
                .stream()
                .filter(assignment -> isVisibleInCourse(assignment, lmsCourseId))
                .sorted(LmsAssignment.BY_DUE_AT)
                .map(LmsCourseAssignmentResponse::of)
                .toList();
    }

    /**
     * One published assignment of a course the student may use. A draft, an archived assignment, one of
     * another course, and one in a course the student may not use all answer the same 404 as one that does
     * not exist, so the answer never reveals which assignments exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public LmsCourseAssignmentDetailResponse getPublishedAssignment(Long userAccountId, Long lmsCourseId, Long assignmentId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw assignmentUnavailable();
        }
        return assignments.findWithCourseByIdAndLmsCourseId(assignmentId, lmsCourseId)
                .filter(assignment -> isVisibleInCourse(assignment, lmsCourseId))
                .map(LmsCourseAssignmentDetailResponse::of)
                .orElseThrow(LmsStudentAssignmentQueryService::assignmentUnavailable);
    }

    // The queries already ask for this course and status. Both are checked again here, so what a student
    // sees never depends on a query alone.
    private static boolean isVisibleInCourse(LmsAssignment assignment, Long lmsCourseId) {
        return assignment.getStatus() == LmsContentStatus.PUBLISHED && lmsCourseId.equals(assignment.getLmsCourse().getId());
    }

    private static ApiException assignmentUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_UNAVAILABLE_MESSAGE);
    }
}
