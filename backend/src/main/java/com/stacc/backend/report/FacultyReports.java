package com.stacc.backend.report;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

public final class FacultyReports {

    private FacultyReports() {
    }

    public record FacultyReportCourseSummary(
            Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            int semesterNumber,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(nullable = true, description = "Null when the offering has no LMS course.") Long lmsCourseId,
            @Schema(nullable = true, allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"}) String lmsCourseStatus,
            @Schema(description = "Course enrollments in ENROLLED status.") long activeCourseEnrollmentCount,
            @Schema(nullable = true, description = "Draft and published assignments; null when the offering has no LMS course faculty can open (none, or archived).")
            Long assignmentCount,
            long finalizedAttendanceSessionCount,
            long publishedResultCount) {
    }

    public record FacultyReportCourse(
            Long courseOfferingId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            int semesterNumber,
            String programCode,
            String programName,
            String academicSessionCode,
            @Schema(allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}) String courseOfferingStatus) {
    }

    public record FacultyEnrollmentReport(long enrolledCount, long completedCount, long withdrawnCount) {
    }

    public record FacultyAssignmentReport(
            @Schema(description = "Whether the offering has a draft or published LMS course to count in.") boolean available,
            @Schema(nullable = true) Long lmsCourseId,
            @Schema(nullable = true, allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"}) String lmsCourseStatus,
            long draftAssignmentCount,
            long publishedAssignmentCount,
            @Schema(description = "Logical submissions to published assignments: one per student and assignment with at least one attempt.")
            long submissionCount,
            @Schema(description = "Submissions to published assignments whose latest attempt has no grade.")
            long needsGradingCount) {
    }

    public record FacultyAttendanceReport(long openAttendanceSessionCount, long finalizedAttendanceSessionCount,
            long presentRecordCount, long absentRecordCount) {
    }

    public record FacultyResultReport(long draftResultCount, long publishedResultCount) {
    }

    public record FacultyCourseReport(FacultyReportCourse course, FacultyEnrollmentReport enrollment,
            FacultyAssignmentReport assignments, FacultyAttendanceReport attendance, FacultyResultReport results) {
    }
}
