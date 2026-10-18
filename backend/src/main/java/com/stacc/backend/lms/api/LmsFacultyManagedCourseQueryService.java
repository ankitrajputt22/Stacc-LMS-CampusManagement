package com.stacc.backend.lms.api;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every course offering a faculty member currently teaches, with its LMS course when it has one. Unlike the faculty
 * course list, it starts from official teaching assignments alone, so offerings without an LMS course and those
 * whose LMS course is archived are listed too: that is what a faculty member manages. It only reads.
 */
@Service
public class LmsFacultyManagedCourseQueryService {

    private static final Comparator<LmsFacultyManagedCourseResponse> BY_COURSE_CODE = Comparator
            .comparing(LmsFacultyManagedCourseResponse::courseCode)
            .thenComparing(LmsFacultyManagedCourseResponse::courseOfferingId);

    private final TeachingAssignmentRepository teachingAssignments;
    private final LmsCourseRepository lmsCourses;

    public LmsFacultyManagedCourseQueryService(TeachingAssignmentRepository teachingAssignments, LmsCourseRepository lmsCourses) {
        this.teachingAssignments = teachingAssignments;
        this.lmsCourses = lmsCourses;
    }

    /**
     * The offerings of the faculty member behind this account, in course code order, in two queries. An assignment
     * counts under {@link FacultyTeachingAccessService#isCurrentTeaching}: an active faculty profile and an active
     * teaching assignment. Nothing is created here.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public List<LmsFacultyManagedCourseResponse> getManagedCourses(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }
        // Consistent data has one assignment per offering; a stray second one never lists an offering twice.
        Map<Long, CourseOffering> offerings = new LinkedHashMap<>();
        for (TeachingAssignment assignment : teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId)) {
            if (FacultyTeachingAccessService.isCurrentTeaching(assignment, userAccountId)) {
                offerings.putIfAbsent(assignment.getCourseOffering().getId(), assignment.getCourseOffering());
            }
        }
        if (offerings.isEmpty()) {
            return List.of();
        }
        Map<Long, LmsCourse> lmsCourseByOffering = lmsCourses.findAllByCourseOfferingIdIn(offerings.keySet()).stream()
                .collect(Collectors.toMap(lmsCourse -> lmsCourse.getCourseOffering().getId(), Function.identity()));
        return offerings.values().stream()
                .map(offering -> responseOf(offering, lmsCourseByOffering.get(offering.getId())))
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    // The course details are read from the academic records every time. The LMS keeps no copy.
    private static LmsFacultyManagedCourseResponse responseOf(CourseOffering offering, LmsCourse lmsCourse) {
        Course course = offering.getCourse();
        Semester semester = offering.getSemester();
        return new LmsFacultyManagedCourseResponse(
                offering.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode(),
                lmsCourse == null ? null : lmsCourse.getId(),
                lmsCourse == null ? null : lmsCourse.getStatus().name());
    }
}
