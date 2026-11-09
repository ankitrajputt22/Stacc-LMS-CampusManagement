package com.stacc.backend.academic.attendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.student.StudentProfile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Made-up course offerings, faculty, and enrollments for the attendance tests, with IDs set the way the database
 * would. There is no LMS course anywhere: attendance needs none.
 */
public final class AttendanceFixtures {

    private static final Department DEPARTMENT = new Department("CSE", "Computer Science and Engineering");
    private static final Program PROGRAM = new Program(DEPARTMENT, "BTECH", "Bachelor of Technology", 8);
    private static final AcademicSession SESSION =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private static final Semester SEMESTER = new Semester(PROGRAM, SESSION, 3);

    private AttendanceFixtures() {
    }

    /** A course offering in semester 3 of the 2026-27 B.Tech. */
    public static CourseOffering offering(long id, String courseCode, String courseName) {
        CourseOffering offering = new CourseOffering(new Course(DEPARTMENT, courseCode, courseName, new BigDecimal("4.00")), SEMESTER);
        ReflectionTestUtils.setField(offering, "id", id);
        return offering;
    }

    /** An active faculty profile with its own account. */
    public static FacultyProfile faculty(long accountId, long profileId) {
        UserAccount account = new UserAccount("EMP-TEST-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        FacultyProfile faculty = new FacultyProfile(account, DEPARTMENT);
        ReflectionTestUtils.setField(faculty, "id", profileId);
        return faculty;
    }

    /** An active teaching assignment of the faculty member to the offering. */
    public static TeachingAssignment teaching(FacultyProfile faculty, CourseOffering offering) {
        return new TeachingAssignment(faculty, offering);
    }

    /** A student's enrollment in the offering, with an account, profile, and semester enrollment. */
    public static CourseEnrollment enrollment(CourseOffering offering, long accountId, long enrollmentId, String loginId) {
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        StudentProfile student = new StudentProfile(account, PROGRAM, SESSION);
        CourseEnrollment enrollment = new CourseEnrollment(new SemesterEnrollment(student, offering.getSemester()), offering);
        ReflectionTestUtils.setField(enrollment, "id", enrollmentId);
        return enrollment;
    }

    /** An open session of the offering. */
    public static AttendanceSession session(CourseOffering offering, FacultyProfile creator, long id, String heldAt) {
        AttendanceSession session = new AttendanceSession(offering, creator, Instant.parse(heldAt), "Topic " + id);
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    /** An unmarked record of the enrollment in the session. */
    public static AttendanceRecord record(AttendanceSession session, CourseEnrollment enrollment, long id) {
        AttendanceRecord record = new AttendanceRecord(session, enrollment);
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }
}
