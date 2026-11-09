package com.stacc.backend.academic.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import org.junit.jupiter.api.Test;

class CourseEnrollmentTest {

    private final UserAccount account = new UserAccount("2408400100011", "example-hash-value");
    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final StudentProfile student = new StudentProfile(account, program, session);
    private final SemesterEnrollment semesterEnrollment = new SemesterEnrollment(student, semester);
    private final Course course = new Course(department, "BCS301", "Data Structures", new BigDecimal("4.00"));
    private final CourseOffering offering = new CourseOffering(course, semester);

    @Test
    void newEnrollmentIsEnrolledAndLinksTheSemesterEnrollmentAndTheOffering() {
        CourseEnrollment enrollment = new CourseEnrollment(semesterEnrollment, offering);

        assertSame(semesterEnrollment, enrollment.getSemesterEnrollment());
        assertSame(offering, enrollment.getCourseOffering());
        assertEquals(CourseEnrollmentStatus.ENROLLED, enrollment.getStatus());
    }

    @Test
    void semesterEnrollmentAndOfferingAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new CourseEnrollment(null, offering));
        assertThrows(IllegalArgumentException.class, () -> new CourseEnrollment(semesterEnrollment, null));
    }

    @Test
    void studentAndAccountAreReachedThroughTheSemesterEnrollment() {
        CourseEnrollment enrollment = new CourseEnrollment(semesterEnrollment, offering);

        assertSame(student, enrollment.getSemesterEnrollment().getStudentProfile());
        assertSame(account, enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount());
        assertEquals(
                "2408400100011",
                enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId());
    }

    @Test
    void courseSemesterProgramAndSessionAreReachedWithoutBeingStoredAgain() {
        CourseEnrollment enrollment = new CourseEnrollment(semesterEnrollment, offering);

        assertSame(course, enrollment.getCourseOffering().getCourse());
        assertSame(semester, enrollment.getSemesterEnrollment().getSemester());
        assertSame(semester, enrollment.getCourseOffering().getSemester());
        assertSame(program, enrollment.getCourseOffering().getSemester().getProgram());
        assertSame(session, enrollment.getCourseOffering().getSemester().getAcademicSession());
        assertSame(department, enrollment.getCourseOffering().getCourse().getDepartment());
    }

    @Test
    void oneSemesterEnrollmentCanHoldSeveralCourses() {
        Course second = new Course(department, "BCS302", "Operating Systems", new BigDecimal("3.00"));

        CourseEnrollment first = new CourseEnrollment(semesterEnrollment, offering);
        CourseEnrollment other = new CourseEnrollment(semesterEnrollment, new CourseOffering(second, semester));

        assertSame(first.getSemesterEnrollment(), other.getSemesterEnrollment());
        assertEquals("BCS302", other.getCourseOffering().getCourse().getCode());
    }

    @Test
    void enrollmentCanBeCompletedOrWithdrawn() {
        CourseEnrollment completed = new CourseEnrollment(semesterEnrollment, offering);
        CourseEnrollment withdrawn = new CourseEnrollment(semesterEnrollment, offering);

        completed.markCompleted();
        withdrawn.withdraw();

        assertEquals(CourseEnrollmentStatus.COMPLETED, completed.getStatus());
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
    }

    @Test
    void enrollmentThatHasEndedCannotChangeAgain() {
        CourseEnrollment completed = new CourseEnrollment(semesterEnrollment, offering);
        completed.markCompleted();
        CourseEnrollment withdrawn = new CourseEnrollment(semesterEnrollment, offering);
        withdrawn.withdraw();

        assertThrows(IllegalStateException.class, completed::withdraw);
        assertThrows(IllegalStateException.class, withdrawn::markCompleted);
        assertEquals(CourseEnrollmentStatus.COMPLETED, completed.getStatus());
        assertEquals(CourseEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
    }
}
