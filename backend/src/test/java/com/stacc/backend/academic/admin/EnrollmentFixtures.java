package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;

/**
 * A small made-up college for the offering and enrollment tests: one program with semesters 3 and 4 in one session,
 * a second program, two courses, offerings of both courses in semester 3 and of the first in semester 4, and one
 * student of the first program. IDs are set the way the database would.
 */
final class EnrollmentFixtures {

    final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    final Program mtech = MasterFixtures.program(6, cse, "MTECH-CSE", 4);
    final AcademicSession session = MasterFixtures.session(2, "2026-27", 2026);
    final Semester third = MasterFixtures.semester(9, btech, session, 3);
    final Semester fourth = MasterFixtures.semester(10, btech, session, 4);
    final Semester mtechFirst = MasterFixtures.semester(11, mtech, session, 1);
    final Course dataStructures = MasterFixtures.course(12, cse, "CS301", "4.00");
    final Course databases = MasterFixtures.course(13, cse, "CS302", "3.00");
    final CourseOffering offeringA = MasterFixtures.withId(new CourseOffering(dataStructures, third), 31);
    final CourseOffering offeringB = MasterFixtures.withId(new CourseOffering(databases, third), 32);
    final CourseOffering fourthSemesterOffering = MasterFixtures.withId(new CourseOffering(dataStructures, fourth), 33);
    final StudentProfile studentA = student(57, 70, "STU-TEST-001");

    StudentProfile student(long profileId, long accountId, String loginId) {
        UserAccount account = MasterFixtures.withId(new UserAccount(loginId, "example-hash-value"), accountId);
        return MasterFixtures.withId(new StudentProfile(account, btech, session), profileId);
    }

    SemesterEnrollment semesterEnrollment(StudentProfile student, Semester semester, long id) {
        return MasterFixtures.withId(new SemesterEnrollment(student, semester), id);
    }

    CourseEnrollment courseEnrollment(SemesterEnrollment semesterEnrollment, CourseOffering offering, long id) {
        return MasterFixtures.withId(new CourseEnrollment(semesterEnrollment, offering), id);
    }
}
