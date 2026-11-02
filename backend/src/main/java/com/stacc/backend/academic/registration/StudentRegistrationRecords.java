package com.stacc.backend.academic.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requests and answers of student course registration. None of them names the student: that comes from the token. */
public final class StudentRegistrationRecords {

    /** The most offerings one registration may name. */
    public static final int MAX_OFFERINGS_PER_REQUEST = 20;

    private StudentRegistrationRecords() {
    }

    /** The offerings to register for, all of them or none. */
    public record RegistrationRequest(
            @Schema(description = "The course offerings to register for, each once, at most 20.", example = "[101, 102]")
            @NotEmpty(message = "Choose at least one course offering")
            @Size(max = MAX_OFFERINGS_PER_REQUEST, message = "Choose at most 20 course offerings at once")
            List<@NotNull(message = "Course offering IDs cannot be empty") Long> courseOfferingIds) {
    }

    /** What the student sees on the registration page: one term per semester they may register in now. */
    public record RegistrationOverview(
            @Schema(description = "Whether the student profile is active. An inactive profile cannot register or drop.")
            boolean profileActive,
            @Schema(description = "Each semester of the current academic session the student is enrolled in.")
            List<RegistrationTerm> terms) {
    }

    public record RegistrationTerm(
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "Bachelor of Technology in CSE") String programName,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "The student's section, or null.", example = "A") String sectionCode,
            @Schema(description = "When registration opens, in UTC, or null when no period is set.")
            Instant registrationOpensAt,
            @Schema(description = "When registration closes, in UTC, or null.") Instant registrationClosesAt,
            @Schema(description = "The last moment to drop a course, in UTC, or null when dropping is not allowed.")
            Instant dropDeadline,
            @Schema(description = "Whether registration is open right now.") boolean registrationOpen,
            @Schema(description = "Whether a registered course can be dropped right now.") boolean dropOpen,
            @Schema(description = "The program's credit limit per semester, or null for no limit.", example = "24")
            BigDecimal maxCredits,
            @Schema(description = "Credits of the courses the student is enrolled in now in this semester.", example = "16")
            BigDecimal registeredCredits,
            @Schema(description = "Offerings open for registration to this student's semester and section.")
            List<EligibleOffering> offerings,
            @Schema(description = "Every course enrollment of this semester, whatever its status.")
            List<RegisteredCourse> registered) {
    }

    public record EligibleOffering(
            @Schema(example = "101") Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4") BigDecimal credits,
            @Schema(allowableValues = {"CORE", "ELECTIVE", "LAB", "PROJECT", "AUDIT"}, example = "CORE") String courseType,
            @Schema(description = "The section it is offered to, or null for the whole semester.", example = "A")
            String sectionCode,
            @Schema(description = "Names of the faculty actively teaching it, primary first.") List<String> faculty,
            @Schema(description = "Places in total, or null for no limit.", example = "60") Integer capacity,
            @Schema(description = "Places still free, or null for no limit.", example = "8") Integer availableSeats,
            @Schema(description = "Whether the student can register for it now.") boolean canRegister,
            @Schema(description = "Why not, in plain words, or null when they can.", example = "This course is full.")
            String reason) {
    }

    public record RegisteredCourse(
            @Schema(example = "501") Long courseEnrollmentId,
            @Schema(example = "101") Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4") BigDecimal credits,
            @Schema(example = "A") String sectionCode,
            List<String> faculty,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN", "DROPPED"}, example = "ENROLLED") String status,
            @Schema(description = "When the enrollment was made, in UTC.") Instant registeredAt,
            @Schema(description = "When it was dropped, in UTC, or null.") Instant droppedAt,
            @Schema(description = "Whether the student can drop it now.") boolean canDrop) {
    }

    /** The answer to a successful registration. */
    public record RegistrationResult(
            List<RegisteredCourse> registered,
            @Schema(example = "2") int totalCourses,
            @Schema(example = "8") BigDecimal totalCredits) {
    }
}
