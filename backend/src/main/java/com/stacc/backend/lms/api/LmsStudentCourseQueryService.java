package com.stacc.backend.lms.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.admin.OfferingFacultyLoader;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessPolicy;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsStudentCourseQueryService {

    static final String COURSE_UNAVAILABLE_MESSAGE = "Course not found or unavailable.";

    private static final Comparator<LmsCourseSummaryResponse> BY_COURSE_CODE = Comparator
            .comparing(LmsCourseSummaryResponse::courseCode)
            .thenComparing(LmsCourseSummaryResponse::lmsCourseId);

    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentAccessService accessService;
    private final OfferingFacultyLoader faculty;

    public LmsStudentCourseQueryService(
            CourseEnrollmentRepository courseEnrollments,
            LmsCourseRepository lmsCourses,
            LmsStudentAccessService accessService,
            OfferingFacultyLoader faculty) {
        this.courseEnrollments = courseEnrollments;
        this.lmsCourses = lmsCourses;
        this.accessService = accessService;
        this.faculty = faculty;
    }

    @Transactional(readOnly = true)
    public List<LmsCourseSummaryResponse> getAccessibleCourses(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }
        List<CourseEnrollment> enrollments = courseEnrollments.findAllWithStudentByUserAccountId(userAccountId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Map<Long, LmsCourse> byOffering = lmsCourses.findAllWithAcademicContextByCourseOfferingIdIn(enrollments.stream()
                        .map(enrollment -> enrollment.getCourseOffering().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(course -> course.getCourseOffering().getId(), Function.identity()));
        List<LmsCourse> accessible = enrollments.stream()
                .filter(enrollment -> byOffering.containsKey(enrollment.getCourseOffering().getId()))
                .filter(enrollment -> LmsStudentAccessPolicy.grantsCurrentAccess(enrollment,
                        byOffering.get(enrollment.getCourseOffering().getId()), userAccountId))
                .map(enrollment -> byOffering.get(enrollment.getCourseOffering().getId()))
                .distinct()
                .toList();
        if (accessible.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> names = faculty.activeNamesByOffering(
                accessible.stream().map(course -> course.getCourseOffering().getId()).toList());
        return accessible.stream()
                .map(course -> summaryOf(course, names.getOrDefault(course.getCourseOffering().getId(), List.of())))
                .distinct()
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    @Transactional(readOnly = true)
    public LmsCourseDetailResponse getAccessibleCourse(Long userAccountId, Long lmsCourseId) {
        if (!accessService.canAccessCourse(userAccountId, lmsCourseId)) {
            throw courseUnavailable();
        }
        LmsCourse lmsCourse = lmsCourses.findWithAcademicContextById(lmsCourseId)
                .orElseThrow(LmsStudentCourseQueryService::courseUnavailable);
        Long offeringId = lmsCourse.getCourseOffering().getId();
        return detailOf(lmsCourse, faculty.activeNamesByOffering(List.of(offeringId)).getOrDefault(offeringId, List.of()));
    }

    private static ApiException courseUnavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE_MESSAGE);
    }

    private static LmsCourseSummaryResponse summaryOf(LmsCourse lmsCourse, List<String> facultyNames) {
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
                semester.getAcademicSession().getCode(),
                courseOffering.getSection() == null ? null : courseOffering.getSection().getCode(),
                facultyNames);
    }

    private static LmsCourseDetailResponse detailOf(LmsCourse lmsCourse, List<String> facultyNames) {
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
                semester.getAcademicSession().getCode(),
                courseOffering.getSection() == null ? null : courseOffering.getSection().getCode(),
                facultyNames,
                lmsCourse.getHomeView(),
                lmsCourse.getWelcomeMessage());
    }
}
