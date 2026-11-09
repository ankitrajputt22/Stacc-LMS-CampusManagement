package com.stacc.backend.academic.admin;

import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The official academic mapping of students and faculty as admins see and correct it: never contact details. A
 * student's current session, semester, and section are worked out from their enrolled semester enrollment, not stored.
 */
public final class AcademicProfileRecords {

    private AcademicProfileRecords() {
    }

    @Schema(description = "Where a student studies now: their enrolled semester enrollment of the latest session.")
    public record CurrentPlacement(
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "The section, or null when not placed in one.", example = "A") String sectionCode) {
    }

    public record StudentProfileSummary(
            @Schema(example = "57") Long studentProfileId,
            @Schema(description = "The student ID, which is the account's login ID.", example = "student1") String loginId,
            @Schema(description = "The name from the person profile, or null.") String name,
            @Schema(description = "The roll number, or null.", example = "21CSE045") String rollNumber,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "Bachelor of Technology in CSE") String programName,
            @Schema(example = "CSE") String departmentCode,
            @Schema(description = "The session of original admission only.", example = "2025-26") String admissionSessionCode,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status,
            @Schema(description = "Null when the student is not enrolled in any semester now.") CurrentPlacement current) {
    }

    public record StudentProfileUpdate(
            @Schema(description = "Up to 30 characters, stored in uppercase, unique. Blank clears it.", example = "21CSE045")
            @Size(max = StudentProfile.MAX_ROLL_NUMBER_LENGTH, message = "Roll number must be at most 30 characters")
            String rollNumber,

            @Schema(description = "ACTIVE or INACTIVE. An inactive profile takes no new enrollments and opens no LMS course.",
                    allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            StudentProfileStatus status) {
    }

    public record FacultyProfileSummary(
            @Schema(example = "11") Long facultyProfileId,
            @Schema(description = "The employee ID, which is the account's login ID.", example = "faculty1") String loginId,
            @Schema(description = "The name from the person profile, or null.") String name,
            @Schema(example = "3") Long departmentId,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status,
            @Schema(description = "How many course offerings they actively teach.", example = "3") long activeTeachingCount) {
    }

    public record FacultyProfileUpdate(
            @Schema(description = "The primary department, active. It never limits what they teach.", example = "3")
            @NotNull(message = "Department is required")
            Long departmentId,

            @Schema(description = "ACTIVE or INACTIVE. An inactive profile cannot be assigned and loses teaching access.",
                    allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            FacultyProfileStatus status) {
    }
}
