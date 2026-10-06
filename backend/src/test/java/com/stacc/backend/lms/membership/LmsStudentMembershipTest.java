package com.stacc.backend.lms.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.junit.jupiter.api.Test;

class LmsStudentMembershipTest {

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
    private final CourseEnrollment courseEnrollment = new CourseEnrollment(semesterEnrollment, offering);
    private final LmsCourse lmsCourse = new LmsCourse(offering);

    @Test
    void newMembershipIsActiveAndLinksTheCourseEnrollmentAndTheLmsCourse() {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

        assertSame(courseEnrollment, membership.getCourseEnrollment());
        assertSame(lmsCourse, membership.getLmsCourse());
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
    }

    @Test
    void courseEnrollmentAndLmsCourseAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsStudentMembership(null, lmsCourse));
        assertThrows(IllegalArgumentException.class, () -> new LmsStudentMembership(courseEnrollment, null));
    }

    @Test
    void membershipCanBeDeactivatedAndActivatedAgain() {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

        membership.deactivate();
        assertEquals(LmsStudentMembershipStatus.INACTIVE, membership.getStatus());

        membership.activate();
        assertEquals(LmsStudentMembershipStatus.ACTIVE, membership.getStatus());
    }

    @Test
    void changingTheMembershipLeavesTheCourseEnrollmentAndTheLmsCourseAlone() {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

        membership.deactivate();

        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
    }

    @Test
    void studentAndAccountAreReachedThroughTheCourseEnrollment() {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

        StudentProfile reached = membership.getCourseEnrollment().getSemesterEnrollment().getStudentProfile();

        assertSame(student, reached);
        assertSame(account, reached.getUserAccount());
        assertEquals("2408400100011", reached.getUserAccount().getLoginId());
    }

    @Test
    void courseOfferingIsReachedFromBothSidesAndTheRestThroughIt() {
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

        assertSame(offering, membership.getCourseEnrollment().getCourseOffering());
        assertSame(offering, membership.getLmsCourse().getCourseOffering());
        assertSame(course, membership.getLmsCourse().getCourseOffering().getCourse());
        assertSame(semester, membership.getLmsCourse().getCourseOffering().getSemester());
        assertSame(program, membership.getLmsCourse().getCourseOffering().getSemester().getProgram());
        assertSame(session, membership.getLmsCourse().getCourseOffering().getSemester().getAcademicSession());
    }

    @Test
    void courseEnrollmentAndLmsCourseAreTheOnlyLinksStoredOnTheMembership() {
        List<String> fields = Arrays.stream(LmsStudentMembership.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(
                List.of("id", "courseEnrollment", "lmsCourse", "status", "createdAt", "updatedAt"), fields);
    }

    @Test
    void severalStudentsCanBelongToTheSameLmsCourse() {
        UserAccount classmateAccount = new UserAccount("2408400100012", "example-hash-value");
        StudentProfile classmate = new StudentProfile(classmateAccount, program, session);
        CourseEnrollment classmateEnrollment =
                new CourseEnrollment(new SemesterEnrollment(classmate, semester), offering);

        LmsStudentMembership first = new LmsStudentMembership(courseEnrollment, lmsCourse);
        LmsStudentMembership second = new LmsStudentMembership(classmateEnrollment, lmsCourse);

        assertSame(first.getLmsCourse(), second.getLmsCourse());
        assertSame(classmate, second.getCourseEnrollment().getSemesterEnrollment().getStudentProfile());
    }
}
