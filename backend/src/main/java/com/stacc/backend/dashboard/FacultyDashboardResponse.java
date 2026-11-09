package com.stacc.backend.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in faculty member's dashboard: short previews of the course offerings they officially teach, upcoming
 * assignments, submissions waiting for a grade, and open attendance sessions, each with its full count. Nothing is
 * derived beyond what the owning features already hold: no averages, ranks, scores, or progress.
 */
public record FacultyDashboardResponse(
        @Schema(description = "How many course offerings the faculty member teaches now, with or without an LMS course.",
                example = "4")
        int teachingCourseCount,

        @Schema(description = "The first of them, in course code order, at most "
                + FacultyDashboardQueryService.TEACHING_PREVIEW_LIMIT + ".")
        List<TeachingCourse> teachingCourses,

        @Schema(description = "How many draft or published assignments of their draft or published LMS courses are due "
                + "now or later.", example = "6")
        long upcomingAssignmentCount,

        @Schema(description = "The nearest of those deadlines first, at most "
                + FacultyDashboardQueryService.ASSIGNMENT_PREVIEW_LIMIT + ".")
        List<UpcomingAssignment> upcomingAssignments,

        @Schema(description = "How many submissions to published assignments have a latest attempt without a grade.",
                example = "3")
        long needsGradingCount,

        @Schema(description = "The longest-waiting of those first, at most "
                + FacultyDashboardQueryService.GRADING_PREVIEW_LIMIT + ".")
        List<PendingSubmission> needsGrading,

        @Schema(description = "How many attendance sessions of their course offerings are still open.", example = "2")
        long openAttendanceCount,

        @Schema(description = "The latest class first, at most " + FacultyDashboardQueryService.ATTENDANCE_PREVIEW_LIMIT + ".")
        List<OpenAttendanceSession> openAttendanceSessions) {

    public record TeachingCourse(
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH") String programCode,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(description = "Null when the offering has no LMS course yet.", example = "42", nullable = true)
            Long lmsCourseId,
            @Schema(description = "Null when the offering has no LMS course yet.",
                    allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"}, example = "PUBLISHED", nullable = true)
            String lmsCourseStatus) {
    }

    public record UpcomingAssignment(
            @Schema(example = "7") Long assignmentId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Linked list exercises") String title,
            @Schema(description = "An absolute moment in UTC.", example = "2026-10-30T18:29:00Z") Instant dueAt,
            @Schema(example = "20.00") BigDecimal maxMarks,
            @Schema(allowableValues = {"DRAFT", "PUBLISHED"}, example = "DRAFT") String status) {
    }

    public record PendingSubmission(
            @Schema(example = "12") Long submissionId,
            @Schema(example = "7") Long assignmentId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(description = "The student's college ID, the account's login ID.", example = "STU-2026-001")
            String studentLoginId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Linked list exercises") String assignmentTitle,
            @Schema(example = "55") Long latestAttemptId,
            @Schema(example = "2") int latestAttemptNumber,
            @Schema(example = "2026-10-18T09:00:00Z") Instant latestSubmittedAt,
            @Schema(description = "Whether the latest attempt came after the deadline that applied to it.", example = "false")
            boolean latestLate) {
    }

    public record OpenAttendanceSession(
            @Schema(example = "8") Long attendanceSessionId,
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "2026-10-19T04:30:00Z") Instant heldAt,
            @Schema(description = "Plain text, or null.", example = "Arrays introduction") String topic,
            @Schema(description = "Students on the session's roster.", example = "40") long totalRecords,
            @Schema(description = "Records not marked present or absent yet.", example = "3") long unmarkedCount) {
    }
}
