package com.stacc.backend.lms.api;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only answers about assignments, for faculty: one course's assignments, and all assignments of the
 * courses they teach. Which courses count comes from official teaching assignments every time. Archived
 * assignments are kept but not listed.
 */
@Service
@Transactional(readOnly = true)
public class LmsFacultyAssignmentQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";

    private final LmsFacultyAccessService facultyAccess;
    private final LmsFacultyCourseQueryService facultyCourses;
    private final LmsAssignmentRepository assignments;

    public LmsFacultyAssignmentQueryService(
            LmsFacultyAccessService facultyAccess,
            LmsFacultyCourseQueryService facultyCourses,
            LmsAssignmentRepository assignments) {
        this.facultyAccess = facultyAccess;
        this.facultyCourses = facultyCourses;
        this.assignments = assignments;
    }

    /**
     * The draft and published assignments of a course the faculty member teaches, earliest deadline first.
     * A course they may not use answers the same 404 as a course that does not exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsFacultyAssignmentResponse> getManagedAssignments(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
        }
        return assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(lmsCourseId, LmsContentPolicy.IN_USE)
                .stream()
                .filter(assignment -> LmsContentPolicy.isInUse(assignment.getStatus())
                        && lmsCourseId.equals(assignment.getLmsCourse().getId()))
                .sorted(LmsAssignment.BY_DUE_AT)
                .map(LmsFacultyAssignmentResponse::of)
                .toList();
    }

    /**
     * The draft and published assignments of every course the faculty member may use right now, earliest
     * deadline first. The courses are the ones in their course list, decided by the same rule as every
     * single-course check: an active faculty profile, an active teaching assignment, and a draft or
     * published LMS course.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsFacultyAssignmentOverviewResponse> getMyAssignments(Long userAccountId) {
        Map<Long, LmsFacultyCourseSummaryResponse> coursesById = facultyCourses.getAccessibleCourses(userAccountId).stream()
                .collect(Collectors.toMap(LmsFacultyCourseSummaryResponse::lmsCourseId, Function.identity(), (first, same) -> first));
        if (coursesById.isEmpty()) {
            return List.of();
        }
        return assignments.findAllByLmsCourseIdInAndStatusInOrderByDueAtAscIdAsc(coursesById.keySet(), LmsContentPolicy.IN_USE)
                .stream()
                .filter(assignment -> LmsContentPolicy.isInUse(assignment.getStatus())
                        && coursesById.containsKey(assignment.getLmsCourse().getId()))
                .sorted(LmsAssignment.BY_DUE_AT)
                .map(assignment -> overviewOf(assignment, coursesById.get(assignment.getLmsCourse().getId())))
                .toList();
    }

    private static LmsFacultyAssignmentOverviewResponse overviewOf(LmsAssignment assignment, LmsFacultyCourseSummaryResponse course) {
        return new LmsFacultyAssignmentOverviewResponse(
                course.lmsCourseId(),
                course.courseCode(),
                course.courseName(),
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDueAt(),
                assignment.getMaxMarks(),
                assignment.getStatus());
    }
}
