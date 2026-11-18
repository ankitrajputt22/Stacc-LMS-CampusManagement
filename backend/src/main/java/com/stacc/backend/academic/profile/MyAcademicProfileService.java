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
import com.stacc.backend.academic.responsibility.DepartmentHeadResolver;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MyAcademicProfileService {

    private final StudentProfileRepository students;
    private final FacultyProfileRepository faculty;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseResultRepository results;
    private final TeachingAssignmentRepository assignments;
    private final DepartmentHeadResolver heads;
    private final PersonProfileRepository people;

    public MyAcademicProfileService(StudentProfileRepository students, FacultyProfileRepository faculty,
            SemesterEnrollmentRepository semesterEnrollments, CourseEnrollmentRepository courseEnrollments,
            CourseResultRepository results, TeachingAssignmentRepository assignments, DepartmentHeadResolver heads,
            PersonProfileRepository people) {
        this.students = students;
        this.faculty = faculty;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.results = results;
        this.assignments = assignments;
        this.heads = heads;
        this.people = people;
    }

    public MyAcademicProfileResponse profile(Long userAccountId, boolean asStudent, boolean asFaculty) {
        StudentPart student = asStudent ? students.findWithContextByUserAccountId(userAccountId).map(this::studentPart).orElse(null)
                : null;
        FacultyPart facultyPart = asFaculty ? faculty.findWithContextByUserAccountId(userAccountId)
                .map(profile -> new FacultyPart(profile.getUserAccount().getLoginId(), profile.getDepartment().getCode(),
                        profile.getDepartment().getName(), profile.getStatus().name(),
                        assignments.findActiveWithOfferingByFacultyProfileId(profile.getId()).stream()
                                .map(assignment -> teaching(assignment.getCourseOffering(), assignment.getRole().name()))
                                .toList(),
                        headOf(profile.getDepartment().getId())))
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
        Map<Long, List<String>> teachers = teachersOf(courses.values().stream().flatMap(List::stream)
                .map(enrollment -> enrollment.getCourseOffering().getId()).distinct().toList());
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
                                .map(course -> courseRecord(course, published.get(course.getId()),
                                        teachers.getOrDefault(course.getCourseOffering().getId(), List.of()))).toList()))
                        .toList(),
                headOf(program.getDepartment().getId()));
    }

    private String headOf(Long departmentId) {
        return heads.headOf(departmentId)
                .map(account -> people.findByUserAccountId(account.getId()).map(PersonProfile::displayName)
                        .orElse(account.getLoginId()))
                .orElse(null);
    }

    private Map<Long, List<String>> teachersOf(List<Long> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        var active = assignments.findAllWithFacultyByCourseOfferingIdIn(offeringIds).stream()
                .filter(assignment -> assignment.getStatus() == com.stacc.backend.academic.teaching.TeachingAssignmentStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing((com.stacc.backend.academic.teaching.TeachingAssignment assignment) -> assignment.getRole().name())
                        .reversed())
                .toList();
        Map<Long, String> names = people.findAllByUserAccountIdIn(active.stream()
                        .map(assignment -> assignment.getFacultyProfile().getUserAccount().getId()).distinct().toList())
                .stream().collect(Collectors.toMap(person -> person.getUserAccount().getId(), PersonProfile::displayName));
        return active.stream().collect(Collectors.groupingBy(assignment -> assignment.getCourseOffering().getId(),
                Collectors.mapping(assignment -> {
                    var account = assignment.getFacultyProfile().getUserAccount();
                    return names.getOrDefault(account.getId(), account.getLoginId());
                }, Collectors.toList())));
    }

    private static CourseRecord courseRecord(CourseEnrollment enrollment, CourseResult result, List<String> teachers) {
        var course = enrollment.getCourseOffering().getCourse();
        return new CourseRecord(course.getCode(), course.getName(), course.getCredits(), course.getCourseType().name(),
                enrollment.getStatus().name(), result == null ? null
                        : new Result(result.getMarksObtained(), result.getMaxMarks(), result.getGradeCode(), result.getPublishedAt()),
                teachers);
    }

    private static Teaching teaching(CourseOffering offering, String role) {
        var semester = offering.getSemester();
        return new Teaching(offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                semester.getProgram().getCode(), semester.getSemesterNumber(), semester.getAcademicSession().getCode(),
                offering.getSection() == null ? null : offering.getSection().getCode(), role);
    }
}
