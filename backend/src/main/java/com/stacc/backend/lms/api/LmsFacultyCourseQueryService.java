package com.stacc.backend.lms.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.lms.access.LmsFacultyAccessPolicy;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers about the LMS courses a faculty member teaches. */
@Service
public class LmsFacultyCourseQueryService {

    private static final Comparator<LmsFacultyCourseSummaryResponse> BY_COURSE_CODE = Comparator
            .comparing(LmsFacultyCourseSummaryResponse::courseCode)
            .thenComparing(LmsFacultyCourseSummaryResponse::lmsCourseId);

    private final TeachingAssignmentRepository teachingAssignments;
    private final LmsCourseRepository lmsCourses;

    public LmsFacultyCourseQueryService(TeachingAssignmentRepository teachingAssignments, LmsCourseRepository lmsCourses) {
        this.teachingAssignments = teachingAssignments;
        this.lmsCourses = lmsCourses;
    }

    /**
     * The LMS courses of the faculty member behind this account, in course code order. A course is
     * listed only if {@link LmsFacultyAccessPolicy} currently grants access to it, which is the same
     * rule the single-course access check uses. A teaching assignment whose offering has no LMS
     * course yet is simply not listed: nothing is created here.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public List<LmsFacultyCourseSummaryResponse> getAccessibleCourses(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }

        List<TeachingAssignment> assignments = teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        Set<Long> courseOfferingIds = assignments.stream()
                .map(assignment -> assignment.getCourseOffering().getId())
                .collect(Collectors.toSet());
        Map<Long, List<LmsCourse>> lmsCoursesByOffering = lmsCourses.findAllByCourseOfferingIdIn(courseOfferingIds).stream()
                .collect(Collectors.groupingBy(lmsCourse -> lmsCourse.getCourseOffering().getId()));

        return assignments.stream()
                .flatMap(assignment -> lmsCoursesByOffering
                        .getOrDefault(assignment.getCourseOffering().getId(), List.of()).stream()
                        .filter(lmsCourse -> LmsFacultyAccessPolicy.grantsCurrentAccess(assignment, lmsCourse, userAccountId))
                        .map(lmsCourse -> summaryOf(assignment.getCourseOffering(), lmsCourse)))
                // Consistent data never lists a course twice. This keeps the answer clean if it is not.
                .distinct()
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    // The course details are read from the academic records every time. The LMS keeps no copy.
    private static LmsFacultyCourseSummaryResponse summaryOf(CourseOffering courseOffering, LmsCourse lmsCourse) {
        Course course = courseOffering.getCourse();
        Semester semester = courseOffering.getSemester();
        return new LmsFacultyCourseSummaryResponse(
                lmsCourse.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode(),
                lmsCourse.getStatus().name());
    }
}
