package com.stacc.backend.academic.profile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.CourseRecord;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.Current;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.FacultyPart;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.Result;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.SemesterRecord;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.StudentPart;
import com.stacc.backend.academic.profile.MyAcademicProfileResponse.Teaching;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the signed-in person's own academic profile. The account always comes from the access token; nothing here
 * changes data. A student's current session, semester, and section come from their enrolled semester enrollment of the
 * latest session, never from a stored field, and only published results are shown.
 */
@Service
@Transactional(readOnly = true)
public class MyAcademicProfileService {

    private final StudentProfileRepository students;
    private final FacultyProfileRepository faculty;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseResultRepository results;
    private final TeachingAssignmentRepository assignments;

    public MyAcademicProfileService(StudentProfileRepository students, FacultyProfileRepository faculty,
            SemesterEnrollmentRepository semesterEnrollments, CourseEnrollmentRepository courseEnrollments,
            CourseResultRepository results, TeachingAssignmentRepository assignments) {
        this.students = students;
        this.faculty = faculty;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.results = results;
        this.assignments = assignments;
    }

    /**
     * @param userAccountId the signed-in account, from the access token
     * @param asStudent whether the account holds the student role
     * @param asFaculty whether the account holds the faculty role
     */
    public MyAcademicProfileResponse profile(Long userAccountId, boolean asStudent, boolean asFaculty) {
        StudentPart student = asStudent ? students.findWithContextByUserAccountId(userAccountId).map(this::studentPart).orElse(null)
                : null;
        FacultyPart facultyPart = asFaculty ? faculty.findWithContextByUserAccountId(userAccountId)
                .map(profile -> new FacultyPart(profile.getUserAccount().getLoginId(), profile.getDepartment().getCode(),
                        profile.getDepartment().getName(), profile.getStatus().name(),
                        assignments.findActiveWithOfferingByFacultyProfileId(profile.getId()).stream()
                                .map(assignment -> teaching(assignment.getCourseOffering(), assignment.getRole().name()))
                                .toList()))
                .orElse(null) : null;
        return new MyAcademicProfileResponse(student, facultyPart);
    }

    private StudentPart studentPart(StudentProfile profile) {
        List<SemesterEnrollment> semesters = semesterEnrollments.findAllWithContextByStudentProfileId(profile.getId());
        Map<Long, List<CourseEnrollment>> courses = semesters.isEmpty() ? Map.of()
                : courseEnrollments.findAllWithOfferingBySemesterEnrollmentIdIn(semesters.stream().map(SemesterEnrollment::getId).toList())
                        .stream().collect(Collectors.groupingBy(enrollment -> enrollment.getSemesterEnrollment().getId()));
        List<Long> courseEnrollmentIds = courses.values().stream().flatMap(List::stream).map(CourseEnrollment::getId).toList();
        Map<Long, CourseResult> published = courseEnrollmentIds.isEmpty() ? Map.of()
                : results.findAllByCourseEnrollmentIdIn(courseEnrollmentIds).stream().filter(CourseResult::isPublished)
                        .collect(Collectors.toMap(result -> result.getCourseEnrollment().getId(), Function.identity()));
        Current current = semesters.stream().filter(enrollment -> enrollment.getStatus() == SemesterEnrollmentStatus.ENROLLED)
                .findFirst()
                .map(enrollment -> new Current(enrollment.getSemester().getAcademicSession().getCode(),
                        enrollment.getSemester().getSemesterNumber(),
                        enrollment.getSection() == null ? null : enrollment.getSection().getCode()))
                .orElse(null);
        var program = profile.getProgram();
        return new StudentPart(profile.getUserAccount().getLoginId(), profile.getRollNumber(), program.getCode(),
                program.getName(), program.getDegreeType().name(), program.getDepartment().getCode(),
                program.getDepartment().getName(), profile.getAdmissionSession().getCode(), profile.getStatus().name(), current,
                semesters.stream().map(enrollment -> new SemesterRecord(enrollment.getSemester().getAcademicSession().getCode(),
                        enrollment.getSemester().getSemesterNumber(), enrollment.getSemester().getProgram().getCode(),
                        enrollment.getSection() == null ? null : enrollment.getSection().getCode(), enrollment.getStatus().name(),
                        courses.getOrDefault(enrollment.getId(), List.of()).stream()
                                .map(course -> courseRecord(course, published.get(course.getId()))).toList()))
                        .toList());
    }

    private static CourseRecord courseRecord(CourseEnrollment enrollment, CourseResult result) {
        var course = enrollment.getCourseOffering().getCourse();
        return new CourseRecord(course.getCode(), course.getName(), course.getCredits(), course.getCourseType().name(),
                enrollment.getStatus().name(), result == null ? null
                        : new Result(result.getMarksObtained(), result.getMaxMarks(), result.getGradeCode(), result.getPublishedAt()));
    }

    private static Teaching teaching(CourseOffering offering, String role) {
        var semester = offering.getSemester();
        return new Teaching(offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                semester.getProgram().getCode(), semester.getSemesterNumber(), semester.getAcademicSession().getCode(),
                offering.getSection() == null ? null : offering.getSection().getCode(), role);
    }
}
