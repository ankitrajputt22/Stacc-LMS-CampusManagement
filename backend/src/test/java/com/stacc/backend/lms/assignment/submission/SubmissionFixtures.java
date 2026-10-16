package com.stacc.backend.lms.assignment.submission;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up courses, enrollments, and assignments for the submission tests, with IDs set the way the database would. */
final class SubmissionFixtures {

    /** The deadline of every fixture assignment. */
    static final Instant DUE = Instant.parse("2026-10-20T18:00:00Z");

    private SubmissionFixtures() {
    }

    /** A published LMS course with its own course offering. */
    static LmsCourse course(long id) {
        LmsCourse course = ContentFixtures.lmsCourse(id);
        course.publish();
        return course;
    }

    /**
     * A student's membership in the course: an account, an active student profile, a semester enrollment,
     * and a course enrollment for the course's offering.
     */
    static LmsStudentMembership membership(LmsCourse course, long accountId, long courseEnrollmentId) {
        Semester semester = course.getCourseOffering().getSemester();
        UserAccount account = new UserAccount("TEST-STUDENT-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        StudentProfile student = new StudentProfile(account, semester.getProgram(), semester.getAcademicSession());
        CourseEnrollment courseEnrollment = new CourseEnrollment(new SemesterEnrollment(student, semester), course.getCourseOffering());
        ReflectionTestUtils.setField(courseEnrollment, "id", courseEnrollmentId);
        LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, course);
        ReflectionTestUtils.setField(membership, "id", courseEnrollmentId + 1000);
        return membership;
    }

    /** An assignment of the course, due at {@link #DUE}, in the given state. */
    static LmsAssignment assignment(LmsCourse course, long id, LmsContentStatus status) {
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
