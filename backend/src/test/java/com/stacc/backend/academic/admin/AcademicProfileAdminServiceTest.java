package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicProfileRecords.FacultyProfileUpdate;
import com.stacc.backend.academic.admin.AcademicProfileRecords.StudentProfileUpdate;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AcademicProfileAdminServiceTest {

    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final FacultyProfileRepository faculty = mock(FacultyProfileRepository.class);
    private final SemesterEnrollmentRepository semesterEnrollments = mock(SemesterEnrollmentRepository.class);
    private final TeachingAssignmentRepository assignments = mock(TeachingAssignmentRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final AcademicProfileAdminService service = new AcademicProfileAdminService(students, faculty, semesterEnrollments,
            assignments, departments, new OfferingFacultyLoader(assignments, mock(PersonProfileRepository.class)));
    private final EnrollmentFixtures college = new EnrollmentFixtures();

    @Test
    void aStudentListShowsWhereEachStudentStudiesNow() {
        SemesterEnrollment third = college.semesterEnrollment(college.studentA, college.third, 88);
        third.placeIn(MasterFixtures.withId(new Section(college.third, "A", null, null), 21L));
        when(students.searchByLoginIdOrRollNumber(any(), any())).thenReturn(List.of(college.studentA));
        when(semesterEnrollments.findEnrolledWithContextByStudentProfileIdIn(any())).thenReturn(List.of(third));

        var listed = service.students("stu").getFirst();

        assertEquals("STU-TEST-001", listed.loginId());
        assertEquals("BTECH-CSE", listed.programCode());
        assertEquals("CSE", listed.departmentCode());
        assertEquals("2026-27", listed.current().academicSessionCode());
        assertEquals(3, listed.current().semesterNumber());
        assertEquals("A", listed.current().sectionCode());
    }

    @Test
    void aRollNumberIsStoredInUppercaseUniqueAndBlankClearsIt() {
        when(students.findById(57L)).thenReturn(Optional.of(college.studentA));
        when(students.saveAndFlush(college.studentA)).thenReturn(college.studentA);

        assertEquals("21CSE045", service.updateStudent(57L, new StudentProfileUpdate(" 21cse045 ", StudentProfileStatus.ACTIVE))
                .rollNumber());
        assertNull(service.updateStudent(57L, new StudentProfileUpdate("  ", StudentProfileStatus.INACTIVE)).rollNumber());
        assertEquals(StudentProfileStatus.INACTIVE, college.studentA.getStatus());

        when(students.existsByRollNumberAndIdNot("21CSE046", 57L)).thenReturn(true);
        ApiException refused = assertThrows(ApiException.class,
                () -> service.updateStudent(57L, new StudentProfileUpdate("21cse046", StudentProfileStatus.ACTIVE)));
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
        assertEquals(AcademicProfileAdminService.ROLL_TAKEN, refused.getMessage());
    }

    @Test
    void aFacultyMemberMovesOnlyToAnActiveDepartment() {
        UserAccount account = MasterFixtures.withId(new UserAccount("faculty1", "example-hash-value"), 81L);
        FacultyProfile profile = MasterFixtures.withId(new FacultyProfile(account, college.cse), 11L);
        Department ee = MasterFixtures.department(5, "EE", "Electrical Engineering");
        when(faculty.findWithAccountById(11L)).thenReturn(Optional.of(profile));
        when(departments.findById(5L)).thenReturn(Optional.of(ee));
        when(assignments.countActiveByFacultyProfileIdIn(any())).thenReturn(List.<Object[]>of(new Object[] {11L, 2L}));

        ee.deactivate();
        ApiException refused = assertThrows(ApiException.class,
                () -> service.updateFaculty(11L, new FacultyProfileUpdate(5L, FacultyProfileStatus.ACTIVE)));
        assertEquals(AcademicProfileAdminService.DEPARTMENT_INACTIVE, refused.getMessage());
        verify(faculty, never()).saveAndFlush(any());

        ee.activate();
        var moved = service.updateFaculty(11L, new FacultyProfileUpdate(5L, FacultyProfileStatus.ACTIVE));
        assertEquals("EE", moved.departmentCode());
        assertEquals(2, moved.activeTeachingCount());
    }
}
