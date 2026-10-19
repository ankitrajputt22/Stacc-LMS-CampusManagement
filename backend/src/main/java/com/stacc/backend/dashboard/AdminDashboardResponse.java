package com.stacc.backend.dashboard;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The admin dashboard: exact counts of the academic masters and of offerings and current enrollments, and a short
 * preview of course offerings. Nothing about individual students, faculty, or accounts is included, and nothing is
 * derived beyond the counts: no rates, averages, trends, or activity.
 */
public record AdminDashboardResponse(
        AcademicMasters academicMasters,
        AcademicOperations academicOperations,
        @Schema(description = "The first course offerings in the admin list's order: the latest academic session first, "
                + "then program code, semester number, course code, and offering ID. At most "
                + AdminDashboardQueryService.OFFERING_PREVIEW_LIMIT + ".")
        List<CourseOfferingPreview> courseOfferings) {

    /** Every record of each master, whatever its status. */
    public record AcademicMasters(
            @Schema(example = "4") long departmentCount,
            @Schema(example = "6") long programCount,
            @Schema(example = "3") long academicSessionCount,
            @Schema(example = "12") long semesterCount,
            @Schema(example = "58") long courseCount) {
    }

    public record AcademicOperations(
            @Schema(description = "Every course offering, whatever its status. Catalog courses and LMS courses are not "
                    + "counted.", example = "24")
            long courseOfferingCount,
            @Schema(description = "Semester enrollments that are ENROLLED; completed and withdrawn ones are not counted.",
                    example = "180")
            long activeSemesterEnrollmentCount,
            @Schema(description = "Course enrollments that are ENROLLED; completed and withdrawn ones are not counted.",
                    example = "920")
            long activeCourseEnrollmentCount) {
    }

    public record CourseOfferingPreview(
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH") String programCode,
            @Schema(example = "2026-27") String academicSessionCode) {
    }
}
