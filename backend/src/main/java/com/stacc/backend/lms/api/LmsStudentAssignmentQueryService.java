package com.stacc.backend.lms.api;

import java.time.Clock;
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

@Service
@Transactional(readOnly = true)
public class LmsStudentAssignmentQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";
    static final String ASSIGNMENT_UNAVAILABLE_MESSAGE = "Assignment not found or unavailable.";

    private final LmsStudentAccessService studentAccess;
    private final LmsAssignmentRepository assignments;

    private final Clock clock;

    public LmsStudentAssignmentQueryService(LmsStudentAccessService studentAccess, LmsAssignmentRepository assignments,
            Clock clock) {
        this.clock = clock;
        this.studentAccess = studentAccess;
        this.assignments = assignments;
    }

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

    public LmsCourseAssignmentDetailResponse getPublishedAssignment(Long userAccountId, Long lmsCourseId, Long assignmentId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw assignmentUnavailable();
        }
        return assignments.findWithCourseByIdAndLmsCourseId(assignmentId, lmsCourseId)
                .filter(assignment -> isVisibleInCourse(assignment, lmsCourseId))
                .map(assignment -> LmsCourseAssignmentDetailResponse.of(assignment, clock.instant()))
                .orElseThrow(LmsStudentAssignmentQueryService::assignmentUnavailable);
    }

    private static boolean isVisibleInCourse(LmsAssignment assignment, Long lmsCourseId) {
        return assignment.getStatus() == LmsContentStatus.PUBLISHED && lmsCourseId.equals(assignment.getLmsCourse().getId());
    }

    private static ApiException assignmentUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_UNAVAILABLE_MESSAGE);
    }
}
