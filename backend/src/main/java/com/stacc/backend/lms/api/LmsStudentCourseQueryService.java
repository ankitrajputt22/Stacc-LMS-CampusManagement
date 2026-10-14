package com.stacc.backend.lms.api;

import java.util.Comparator;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessPolicy;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the LMS courses a student may currently use, and shows one of them. It only reads: it never
 * creates a missing membership, and a course the student cannot use is left out or unavailable.
 */
@Service
public class LmsStudentCourseQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";

    private static final Comparator<LmsCourseSummaryResponse> BY_COURSE_CODE = Comparator
            .comparing(LmsCourseSummaryResponse::courseCode)
            .thenComparing(LmsCourseSummaryResponse::lmsCourseId);

    private final LmsStudentMembershipRepository memberships;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentAccessService accessService;

    public LmsStudentCourseQueryService(
            LmsStudentMembershipRepository memberships,
            LmsCourseRepository lmsCourses,
            LmsStudentAccessService accessService) {
        this.memberships = memberships;
        this.lmsCourses = lmsCourses;
        this.accessService = accessService;
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

    /**
     * One course that the student behind this account may use right now. Access is decided again
     * on every call by {@link LmsStudentAccessService}: having seen the course in the list earlier
     * counts for nothing. A course that does not exist and a course the student may not use get the
     * same 404, so the answer never reveals which LMS courses exist or why one is unavailable.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public LmsCourseDetailResponse getAccessibleCourse(Long userAccountId, Long lmsCourseId) {
        if (!accessService.canAccessCourse(userAccountId, lmsCourseId)) {
            throw courseUnavailable();
        }
        return lmsCourses.findWithAcademicContextById(lmsCourseId)
                .map(LmsStudentCourseQueryService::detailOf)
                .orElseThrow(LmsStudentCourseQueryService::courseUnavailable);
    }

    private static ApiException courseUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
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

    // The department is the course's owning department, which may differ from the programme's.
    private static LmsCourseDetailResponse detailOf(LmsCourse lmsCourse) {
        CourseOffering courseOffering = lmsCourse.getCourseOffering();
        Course course = courseOffering.getCourse();
        Department department = course.getDepartment();
        Semester semester = courseOffering.getSemester();
        Program program = semester.getProgram();
        return new LmsCourseDetailResponse(
                lmsCourse.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                department.getCode(),
                department.getName(),
                program.getCode(),
                program.getName(),
                semester.getSemesterNumber(),
                semester.getAcademicSession().getCode());
    }
}
