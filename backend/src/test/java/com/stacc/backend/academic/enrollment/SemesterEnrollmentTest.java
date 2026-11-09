package com.stacc.backend.academic.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import org.junit.jupiter.api.Test;

class SemesterEnrollmentTest {

    private final UserAccount account = new UserAccount("2408400100011", "example-hash-value");
    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession admissionSession =
            new AcademicSession("2024-25", LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30));
    private final AcademicSession currentSession =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final StudentProfile student = new StudentProfile(account, program, admissionSession);
    private final Semester semester = new Semester(program, currentSession, 5);

    @Test
    void newEnrollmentIsEnrolledAndLinksTheStudentAndTheSemester() {
        SemesterEnrollment enrollment = new SemesterEnrollment(student, semester);

        assertSame(student, enrollment.getStudentProfile());
        assertSame(semester, enrollment.getSemester());
        assertEquals(SemesterEnrollmentStatus.ENROLLED, enrollment.getStatus());
    }

    @Test
    void studentAndSemesterAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new SemesterEnrollment(null, semester));
        assertThrows(IllegalArgumentException.class, () -> new SemesterEnrollment(student, null));
    }

    @Test
    void accountProgramAndSessionAreReachedThroughTheStudentAndTheSemester() {
        SemesterEnrollment enrollment = new SemesterEnrollment(student, semester);

        assertSame(account, enrollment.getStudentProfile().getUserAccount());
        assertSame(program, enrollment.getStudentProfile().getProgram());
        assertSame(program, enrollment.getSemester().getProgram());
        assertSame(department, enrollment.getSemester().getProgram().getDepartment());
        assertSame(currentSession, enrollment.getSemester().getAcademicSession());
    }

    @Test
    void sessionOfTheEnrollmentIsNotTheSessionOfAdmission() {
        SemesterEnrollment enrollment = new SemesterEnrollment(student, semester);

        assertSame(admissionSession, enrollment.getStudentProfile().getAdmissionSession());
        assertNotSame(
                enrollment.getStudentProfile().getAdmissionSession(),
                enrollment.getSemester().getAcademicSession());
    }

    @Test
    void oneStudentCanHaveAnEnrollmentForEachSemester() {
        Semester nextSemester = new Semester(program, currentSession, 6);

        SemesterEnrollment first = new SemesterEnrollment(student, semester);
        SemesterEnrollment second = new SemesterEnrollment(student, nextSemester);

        assertSame(first.getStudentProfile(), second.getStudentProfile());
        assertNotSame(first.getSemester(), second.getSemester());
    }

    @Test
    void enrollmentCanBeCompleted() {
        SemesterEnrollment enrollment = new SemesterEnrollment(student, semester);

        enrollment.markCompleted();

        assertEquals(SemesterEnrollmentStatus.COMPLETED, enrollment.getStatus());
    }

    @Test
    void enrollmentCanBeWithdrawn() {
        SemesterEnrollment enrollment = new SemesterEnrollment(student, semester);

        enrollment.withdraw();

        assertEquals(SemesterEnrollmentStatus.WITHDRAWN, enrollment.getStatus());
    }

    @Test
    void enrollmentThatHasEndedCannotChangeAgain() {
        SemesterEnrollment completed = new SemesterEnrollment(student, semester);
        completed.markCompleted();
        SemesterEnrollment withdrawn = new SemesterEnrollment(student, semester);
        withdrawn.withdraw();

        assertThrows(IllegalStateException.class, completed::withdraw);
        assertThrows(IllegalStateException.class, withdrawn::markCompleted);
        assertEquals(SemesterEnrollmentStatus.COMPLETED, completed.getStatus());
        assertEquals(SemesterEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
    }
}
