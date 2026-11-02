package com.stacc.backend.academic.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.responsibility.DepartmentHeadResolver;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MyAcademicProfileServiceTest {

    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final FacultyProfileRepository faculty = mock(FacultyProfileRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final CourseResultRepository results = mock(CourseResultRepository.class);
    private final TeachingAssignmentRepository assignments = mock(TeachingAssignmentRepository.class);
    private final DepartmentHeadResolver heads = mock(DepartmentHeadResolver.class);
    private final PersonProfileRepository people = mock(PersonProfileRepository.class);
    private final MyAcademicProfileService service = new MyAcademicProfileService(students, faculty, semesterEnrollments,
            courseEnrollments, results, assignments, heads, people);

    private static <T> T id(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void aStudentSeesWhereTheyStudyNowAndOnlyPublishedResults() {
        Department cse = id(new Department("CSE", "Computer Science and Engineering"), 3);
        Program btech = id(new Program(cse, "BTECH-CSE", "B.Tech CSE", 8), 4);
        AcademicSession previous = id(new AcademicSession("2025-26", java.time.LocalDate.of(2025, 7, 1), java.time.LocalDate.of(2026, 6, 30)), 1);
        AcademicSession now = id(new AcademicSession("2026-27", java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2027, 6, 30)), 2);
        StudentProfile profile = id(new StudentProfile(id(new UserAccount("student1", "example-hash-value"), 70), btech, previous), 57);
        profile.assignRollNumber("25cse001");
        Semester second = id(new Semester(btech, previous, 2), 8);
        Semester third = id(new Semester(btech, now, 3), 9);
        SemesterEnrollment past = id(new SemesterEnrollment(profile, second), 87);
        past.markCompleted();
        SemesterEnrollment current = id(new SemesterEnrollment(profile, third), 88);
        Course oop = id(new Course(cse, "CS201", "OOP", new BigDecimal("4")), 11);
        Course ds = id(new Course(cse, "CS301", "Data Structures", new BigDecimal("4")), 12);
        CourseEnrollment oopEnrollment = id(new CourseEnrollment(past, id(new CourseOffering(oop, second), 30)), 140);
        CourseEnrollment dsEnrollment = id(new CourseEnrollment(current, id(new CourseOffering(ds, third), 31)), 141);
        var teacher = id(new com.stacc.backend.identity.faculty.FacultyProfile(id(new UserAccount("faculty1", "example-hash-value"), 81), cse), 11);
        CourseResult publishedOop = id(new CourseResult(oopEnrollment, teacher, new BigDecimal("78"), new BigDecimal("100"), "B+", null), 1);
        publishedOop.publish(java.time.Instant.parse("2026-06-01T00:00:00Z"));
        CourseResult draftDs = id(new CourseResult(dsEnrollment, teacher, new BigDecimal("90"), new BigDecimal("100"), "A", null), 2);
        when(students.findWithContextByUserAccountId(70L)).thenReturn(Optional.of(profile));
        when(semesterEnrollments.findAllWithContextByStudentProfileId(57L)).thenReturn(List.of(current, past));
        when(courseEnrollments.findAllWithOfferingBySemesterEnrollmentIdIn(any())).thenReturn(List.of(oopEnrollment, dsEnrollment));
        when(results.findAllByCourseEnrollmentIdIn(any())).thenReturn(List.of(publishedOop, draftDs));
        UserAccount hod = id(new UserAccount("hod1", "example-hash-value"), 90);
        when(heads.headOf(3L)).thenReturn(Optional.of(hod));
        when(people.findByUserAccountId(90L)).thenReturn(Optional.empty());

        var student = service.profile(70L, true, false).student();

        assertEquals("25CSE001", student.rollNumber());
        assertEquals("CSE", student.departmentCode());
        assertEquals("2025-26", student.admissionSessionCode());
        assertEquals("2026-27", student.current().academicSessionCode());
        assertEquals(3, student.current().semesterNumber());
        assertNull(student.semesters().getFirst().courses().getFirst().result(), "a draft result is never shown");
        assertEquals("B+", student.semesters().get(1).courses().getFirst().result().gradeCode());
        assertEquals("hod1", student.headOfDepartment(), "the login ID stands in until a name is recorded");
    }

    @Test
    void aPartIsReadOnlyForARoleTheAccountHolds() {
        var profile = service.profile(70L, false, false);

        assertNull(profile.student());
        assertNull(profile.faculty());
        verifyNoInteractions(students, faculty);
    }
}
