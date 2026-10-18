package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.identity.student.StudentProfile;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Course offerings and official enrollments as admins see them: each record's own ID and status with the codes and
 * names that say what it is. A student appears only by profile ID, login ID, program, and admission session. No
 * password, hash, role, permission, or other account data is ever included, and no entity is sent as it is.
 */
public final class OfferingEnrollmentResponses {

    private OfferingEnrollmentResponses() {
    }

    public record CourseOfferingResponse(
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "12") Long courseId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(description = "An exact decimal with two places.", example = "4.00") BigDecimal credits,
            @Schema(example = "9") Long semesterId,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "2") Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "PLANNED") String status) {

        static CourseOfferingResponse of(CourseOffering offering) {
            Course course = offering.getCourse();
            Semester semester = offering.getSemester();
            Program program = semester.getProgram();
            AcademicSession session = semester.getAcademicSession();
            return new CourseOfferingResponse(offering.getId(), course.getId(), course.getCode(), course.getName(),
                    course.getCredits(), semester.getId(), semester.getSemesterNumber(), program.getId(), program.getCode(),
                    session.getId(), session.getCode(), offering.getStatus().name());
        }
    }

    public record StudentLookupResponse(
            @Schema(example = "57") Long studentProfileId,
            @Schema(description = "The student's college ID, which is the account's login ID.", example = "STU-2026-001")
            String loginId,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "Bachelor of Technology in CSE") String programName,
            @Schema(description = "The session of original admission only, not a current session.", example = "2026-27")
            String admissionSessionCode,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String studentProfileStatus) {

        static StudentLookupResponse of(StudentProfile student) {
            Program program = student.getProgram();
            return new StudentLookupResponse(student.getId(), student.getUserAccount().getLoginId(), program.getId(),
                    program.getCode(), program.getName(), student.getAdmissionSession().getCode(),
                    student.getStatus().name());
        }
    }

    public record SemesterEnrollmentResponse(
            @Schema(example = "88") Long semesterEnrollmentId,
            @Schema(example = "57") Long studentProfileId,
            @Schema(example = "9") Long semesterId,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "2") Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "ENROLLED") String status) {

        static SemesterEnrollmentResponse of(SemesterEnrollment enrollment) {
            Semester semester = enrollment.getSemester();
            Program program = semester.getProgram();
            AcademicSession session = semester.getAcademicSession();
            return new SemesterEnrollmentResponse(enrollment.getId(), enrollment.getStudentProfile().getId(),
                    semester.getId(), semester.getSemesterNumber(), program.getId(), program.getCode(), session.getId(),
                    session.getCode(), enrollment.getStatus().name());
        }
    }

    public record StudentSemesterEnrollmentsResponse(
            StudentLookupResponse student,
            @Schema(description = "The latest academic session first, then the highest semester number.")
            List<SemesterEnrollmentResponse> semesterEnrollments) {
    }

    public record CourseEnrollmentResponse(
            @Schema(example = "140") Long courseEnrollmentId,
            @Schema(example = "88") Long semesterEnrollmentId,
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "12") Long courseId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(description = "An exact decimal with two places.", example = "4.00") BigDecimal credits,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "ENROLLED") String status) {

        static CourseEnrollmentResponse of(CourseEnrollment enrollment) {
            CourseOffering offering = enrollment.getCourseOffering();
            Course course = offering.getCourse();
            return new CourseEnrollmentResponse(enrollment.getId(), enrollment.getSemesterEnrollment().getId(),
                    offering.getId(), course.getId(), course.getCode(), course.getName(), course.getCredits(),
                    enrollment.getStatus().name());
        }
    }

    public record SemesterCourseEnrollmentsResponse(
            StudentLookupResponse student,
            SemesterEnrollmentResponse semesterEnrollment,
            @Schema(description = "By course code.") List<CourseEnrollmentResponse> courseEnrollments) {
    }

    public record CourseEnrollmentCreatedResponse(
            CourseEnrollmentResponse courseEnrollment,
            @Schema(description = "What happened on the LMS side once the official enrollment was saved: PROVISIONED "
                    + "(the student has LMS membership), NO_LMS_COURSE (the offering has no LMS course yet), or FAILED "
                    + "(membership could not be given now; the enrollment stands, and provisioning can be retried).",
                    allowableValues = {"PROVISIONED", "NO_LMS_COURSE", "FAILED"}, example = "PROVISIONED")
            String lmsProvisioning) {
    }

    public record LmsProvisioningResponse(
            @Schema(example = "140") Long courseEnrollmentId,
            @Schema(allowableValues = {"PROVISIONED"}, example = "PROVISIONED") String lmsProvisioning) {
    }

    public record RosterEntryResponse(
            @Schema(example = "140") Long courseEnrollmentId,
            @Schema(example = "88") Long semesterEnrollmentId,
            @Schema(example = "57") Long studentProfileId,
            @Schema(example = "STU-2026-001") String studentLoginId,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "ENROLLED") String status) {

        static RosterEntryResponse of(CourseEnrollment enrollment) {
            SemesterEnrollment semesterEnrollment = enrollment.getSemesterEnrollment();
            StudentProfile student = semesterEnrollment.getStudentProfile();
            return new RosterEntryResponse(enrollment.getId(), semesterEnrollment.getId(), student.getId(),
                    student.getUserAccount().getLoginId(), enrollment.getStatus().name());
        }
    }

    public record OfferingRosterResponse(
            CourseOfferingResponse courseOffering,
            @Schema(description = "Every course enrollment of this offering, whatever its status, by student login ID.")
            List<RosterEntryResponse> enrollments) {
    }
}
