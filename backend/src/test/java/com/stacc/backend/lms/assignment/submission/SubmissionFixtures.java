package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up courses, enrollments, and assignments for the submission tests, with IDs set the way the database would. */
public final class SubmissionFixtures {

    /** The deadline of every fixture assignment. */
    public static final Instant DUE = Instant.parse("2026-10-20T18:00:00Z");

    private SubmissionFixtures() {
    }

    /** A published LMS course with its own course offering. */
    public static LmsCourse course(long id) {
        LmsCourse course = ContentFixtures.lmsCourse(id);
        course.publish();
        return course;
    }

    /**
     * A student's membership in the course: an account, an active student profile, a semester enrollment,
     * and a course enrollment for the course's offering.
     */
    public static LmsStudentMembership membership(LmsCourse course, long accountId, long courseEnrollmentId) {
        return membership(course, accountId, courseEnrollmentId, "TEST-STUDENT-" + accountId);
    }

    /** The same, for an account with this login ID. */
    public static LmsStudentMembership membership(LmsCourse course, long accountId, long courseEnrollmentId, String loginId) {
        Semester semester = course.getCourseOffering().getSemester();
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        StudentProfile student = new StudentProfile(account, semester.getProgram(), semester.getAcademicSession());
        CourseEnrollment courseEnrollment = new CourseEnrollment(new SemesterEnrollment(student, semester), course.getCourseOffering());
        ReflectionTestUtils.setField(courseEnrollment, "id", courseEnrollmentId);
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, course);
        ReflectionTestUtils.setField(membership, "id", courseEnrollmentId + 1000);
        return membership;
    }

    /** A faculty member of the course's department, with an account. */
    public static FacultyProfile faculty(LmsCourse course, long accountId, long profileId) {
        UserAccount account = new UserAccount("TEST-FACULTY-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        FacultyProfile faculty = new FacultyProfile(account, course.getCourseOffering().getCourse().getDepartment());
        ReflectionTestUtils.setField(faculty, "id", profileId);
        return faculty;
    }

    /** An assignment of the course, due at {@link #DUE}, in the given state. */
    public static LmsAssignment assignment(LmsCourse course, long id, LmsContentStatus status) {
        LmsAssignment assignment = new LmsAssignment(course, "Assignment " + id, "Instructions " + id, DUE, new BigDecimal("20"));
        ReflectionTestUtils.setField(assignment, "id", id);
        if (status != LmsContentStatus.DRAFT) {
            assignment.publish(Instant.parse("2026-10-10T09:00:00Z"));
        }
        if (status == LmsContentStatus.ARCHIVED) {
            assignment.archive();
        }
        return assignment;
    }
}
