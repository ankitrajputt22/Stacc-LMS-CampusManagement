package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.offering.OfferingRegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * What an admin may send to create or change a course offering or an enrollment. No request carries an ID of its
 * own or of the record it adds to: the route names those. Other records are named by ID and loaded on the server.
 * New enrollments always start {@code ENROLLED}, so a create request has no status.
 */
public final class OfferingEnrollmentRequests {

    private OfferingEnrollmentRequests() {
    }

    public record CourseOfferingCreate(
            @Schema(description = "The catalog course to teach. It is loaded on the server.", example = "12")
            @NotNull(message = "Course is required")
            Long courseId,

            @Schema(description = "The semester it is taught in. It is loaded on the server.", example = "9")
            @NotNull(message = "Semester is required")
            Long semesterId,

            @Schema(description = "Optional. An active section of that semester; left out, the offering is for the whole "
                    + "semester. Fixed after creation.", example = "21", nullable = true)
            Long sectionId,

            @Schema(description = "Optional. The most enrolled students, from 1 to 1000. Used by course registration "
                    + "from v0.4; no limit when left out.", example = "60", nullable = true)
            @Min(value = 1, message = "Capacity must be at least 1")
            @Max(value = 1000, message = "Capacity must be at most 1000")
            Integer capacity,

            @Schema(description = "DRAFT, OPEN, or CLOSED for course registration. DRAFT when left out.",
                    allowableValues = {"DRAFT", "OPEN", "CLOSED"}, example = "DRAFT", nullable = true)
            OfferingRegistrationStatus registrationStatus) {
    }

    public record CourseOfferingUpdate(
            @Schema(description = "PLANNED, ACTIVE, or CLOSED. An offering cannot go back to PLANNED, and a closed one "
                    + "cannot be made active again.", allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            CourseOfferingStatus status,

            @Schema(description = "Optional. The course is fixed when the offering is created: a different one is "
                    + "refused with 409.", example = "12", nullable = true)
            Long courseId,

            @Schema(description = "Optional. The semester is fixed when the offering is created: a different one is "
                    + "refused with 409.", example = "9", nullable = true)
            Long semesterId,

            @Schema(description = "Optional. The section is fixed when the offering is created: a different one is "
                    + "refused with 409.", example = "21", nullable = true)
            Long sectionId,

            @Schema(description = "The most enrolled students, from 1 to 1000, or null for no limit.", example = "60",
                    nullable = true)
            @Min(value = 1, message = "Capacity must be at least 1")
            @Max(value = 1000, message = "Capacity must be at most 1000")
            Integer capacity,

            @Schema(description = "DRAFT, OPEN, or CLOSED for course registration. Unchanged when left out.",
                    allowableValues = {"DRAFT", "OPEN", "CLOSED"}, example = "OPEN", nullable = true)
            OfferingRegistrationStatus registrationStatus) {
    }

    public record SemesterEnrollmentCreate(
            @Schema(description = "A semester of the student's own program. It is loaded on the server.", example = "9")
            @NotNull(message = "Semester is required")
            Long semesterId) {
    }

    public record SemesterEnrollmentStatusChange(
            @Schema(description = "ENROLLED, COMPLETED, or WITHDRAWN. Only an ENROLLED semester enrollment can change, "
                    + "and only once none of its course enrollments is still ENROLLED.",
                    allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "COMPLETED")
            @NotNull(message = "Status is required")
            SemesterEnrollmentStatus status) {
    }

    public record SemesterEnrollmentSectionChange(
            @Schema(description = "A section of the same semester, or null to take the student out of their section.",
                    example = "21")
            Long sectionId) {
    }

    public record CourseEnrollmentCreate(
            @Schema(description = "An offering of the same semester as the semester enrollment. It is loaded on the "
                    + "server.", example = "31")
            @NotNull(message = "Course offering is required")
            Long courseOfferingId) {
    }

    public record CourseEnrollmentStatusChange(
            @Schema(description = "ENROLLED, COMPLETED, or WITHDRAWN. Only an ENROLLED course enrollment can change.",
                    allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}, example = "COMPLETED")
            @NotNull(message = "Status is required")
            CourseEnrollmentStatus status) {
    }
}
