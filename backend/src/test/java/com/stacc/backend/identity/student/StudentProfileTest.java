package com.stacc.backend.identity.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

class StudentProfileTest {

    private final UserAccount account = new UserAccount("2408400100011", "example-hash-value");
    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession admissionSession =
            new AcademicSession("2024-25", LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30));

    @Test
    void newProfileIsActiveAndLinksAccountProgramAndAdmissionSession() {
        StudentProfile profile = new StudentProfile(account, program, admissionSession);

        assertSame(account, profile.getUserAccount());
        assertSame(program, profile.getProgram());
        assertSame(admissionSession, profile.getAdmissionSession());
        assertEquals(StudentProfileStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void accountProgramAndAdmissionSessionAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new StudentProfile(null, program, admissionSession));
        assertThrows(IllegalArgumentException.class, () -> new StudentProfile(account, null, admissionSession));
        assertThrows(IllegalArgumentException.class, () -> new StudentProfile(account, program, null));
    }

    @Test
    void studentIdAndDepartmentComeFromTheAccountAndTheProgram() {
        StudentProfile profile = new StudentProfile(account, program, admissionSession);

        assertEquals("2408400100011", profile.getUserAccount().getLoginId());
        assertSame(department, profile.getProgram().getDepartment());
    }

    @Test
    void profileCanBeMadeInactiveAndActiveAgain() {
        StudentProfile profile = new StudentProfile(account, program, admissionSession);

        profile.deactivate();
        assertEquals(StudentProfileStatus.INACTIVE, profile.getStatus());

        profile.activate();
        assertEquals(StudentProfileStatus.ACTIVE, profile.getStatus());
    }
}
