package com.stacc.backend.academic.result.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FacultyResultQueryService {

    static final String COURSE_UNAVAILABLE = "Course offering not found or unavailable.";

    private static final Comparator<ResultCourseOfferingResponse> BY_COURSE_CODE = Comparator
            .comparing(ResultCourseOfferingResponse::courseCode)
            .thenComparing(ResultCourseOfferingResponse::courseOfferingId);

    private final TeachingAssignmentRepository teachingAssignments;
    private final FacultyTeachingAccessService teachingAccess;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseResultRepository results;

    public FacultyResultQueryService(
            TeachingAssignmentRepository teachingAssignments,
            FacultyTeachingAccessService teachingAccess,
            CourseEnrollmentRepository courseEnrollments,
            CourseResultRepository results) {
        this.teachingAssignments = teachingAssignments;
        this.teachingAccess = teachingAccess;
        this.courseEnrollments = courseEnrollments;
        this.results = results;
    }

    public List<ResultCourseOfferingResponse> getMyCourseOfferings(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }
        return teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId).stream()
                .filter(assignment -> FacultyTeachingAccessService.isCurrentTeaching(assignment, userAccountId))
                .map(assignment -> ResultCourseOfferingResponse.of(assignment.getCourseOffering()))
                .distinct()
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    public CourseResultRosterResponse getRoster(Long userAccountId, Long courseOfferingId) {
        TeachingAssignment teaching = teachingAccess.findCurrentTeachingAssignment(userAccountId, courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        ResultCourseOfferingResponse course = ResultCourseOfferingResponse.of(teaching.getCourseOffering());

        List<CourseEnrollment> enrollments = courseEnrollments.findAllWithStudentByCourseOfferingId(courseOfferingId);
        Map<Long, CourseResult> resultsByEnrollment = enrollments.isEmpty()
                ? Map.of()
                : results.findAllByCourseEnrollmentIdIn(enrollments.stream().map(CourseEnrollment::getId).toList()).stream()
                        .collect(Collectors.toMap(result -> result.getCourseEnrollment().getId(), Function.identity()));

        List<CourseResultRosterResponse.Student> students = enrollments.stream()
                .map(enrollment -> new CourseResultRosterResponse.Student(
                        enrollment.getId(),
                        enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId(),
                        enrollment.getStatus().name(),
                        FacultyResultResponse.of(resultsByEnrollment.get(enrollment.getId()))))
                .toList();
        return new CourseResultRosterResponse(course.courseOfferingId(), course.courseCode(), course.courseName(),
                course.credits(), course.semesterNumber(), course.programCode(), course.academicSessionCode(), students);
    }
}
