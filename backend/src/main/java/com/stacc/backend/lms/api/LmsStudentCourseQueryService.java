package com.stacc.backend.lms.api;

import java.util.Comparator;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.lms.access.LmsStudentAccessPolicy;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the LMS courses a student may currently use. It only reads: it never creates a missing
 * membership, and a course the student cannot use is simply left out.
 */
@Service
public class LmsStudentCourseQueryService {

    private static final Comparator<LmsCourseSummaryResponse> BY_COURSE_CODE = Comparator
            .comparing(LmsCourseSummaryResponse::courseCode)
            .thenComparing(LmsCourseSummaryResponse::lmsCourseId);

    private final LmsStudentMembershipRepository memberships;

    public LmsStudentCourseQueryService(LmsStudentMembershipRepository memberships) {
        this.memberships = memberships;
    }

    /**
     * The courses of the student behind this account, in course code order. A course is listed
     * only if {@link LmsStudentAccessPolicy} currently grants access to it, which is the same
     * rule the single-course access check uses.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public List<LmsCourseSummaryResponse> getAccessibleCourses(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }
        return memberships.findAllWithCourseDetailsByUserAccountId(userAccountId).stream()
                .filter(membership -> LmsStudentAccessPolicy.grantsCurrentAccess(membership, userAccountId))
                .map(LmsStudentMembership::getLmsCourse)
                .map(LmsStudentCourseQueryService::summaryOf)
                // Consistent data never lists a course twice. This keeps the answer clean if it is not.
                .distinct()
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    // The course details are read from the academic records every time. The LMS keeps no copy.
    private static LmsCourseSummaryResponse summaryOf(LmsCourse lmsCourse) {
        CourseOffering courseOffering = lmsCourse.getCourseOffering();
        Course course = courseOffering.getCourse();
        Semester semester = courseOffering.getSemester();
        return new LmsCourseSummaryResponse(
                lmsCourse.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode());
    }
}
