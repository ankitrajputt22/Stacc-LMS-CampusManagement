package com.stacc.backend.identity.faculty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import org.junit.jupiter.api.Test;

class FacultyProfileTest {

    private final UserAccount account = new UserAccount("EMP-TEST-0001", "example-hash-value");
    private final Department department = new Department("CSE", "Computer Science and Engineering");

    @Test
    void newProfileIsActiveAndLinksAccountAndDepartment() {
        FacultyProfile profile = new FacultyProfile(account, department);

        assertSame(account, profile.getUserAccount());
        assertSame(department, profile.getDepartment());
        assertEquals(FacultyProfileStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void accountAndDepartmentAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new FacultyProfile(null, department));
        assertThrows(IllegalArgumentException.class, () -> new FacultyProfile(account, null));
    }

    @Test
    void employeeIdIsTheAccountsLoginId() {
        FacultyProfile profile = new FacultyProfile(account, department);

        assertEquals("EMP-TEST-0001", profile.getUserAccount().getLoginId());
    }

    @Test
    void profileStoresNoCopyOfAccountOrTeachingData() {
        // The employee ID and name belong to the account, and what someone teaches will come from
        // teaching assignments, so none of them may appear here as a field of its own.
        Set<String> fields = Arrays.stream(FacultyProfile.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "userAccount", "department", "status", "createdAt", "updatedAt"), fields);
    }

    @Test
    void profileCanBeMadeInactiveAndActiveAgain() {
        FacultyProfile profile = new FacultyProfile(account, department);

        profile.deactivate();
        assertEquals(FacultyProfileStatus.INACTIVE, profile.getStatus());

        profile.activate();
        assertEquals(FacultyProfileStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void oneAccountCanHoldBothAStudentAndAFacultyProfile() {
        Department mathematics = new Department("MATH", "Mathematics");
        Program program = new Program(mathematics, "MSC", "Master of Science", 4);
        AcademicSession session = new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));

        StudentProfile studentProfile = new StudentProfile(account, program, session);
        FacultyProfile facultyProfile = new FacultyProfile(account, department);

        assertSame(account, studentProfile.getUserAccount());
        assertSame(account, facultyProfile.getUserAccount());
        // Each profile keeps its own department: the faculty one directly, the student one
        // through the program.
        assertSame(department, facultyProfile.getDepartment());
        assertSame(mathematics, studentProfile.getProgram().getDepartment());
    }
}
