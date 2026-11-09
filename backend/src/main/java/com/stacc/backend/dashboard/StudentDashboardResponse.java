package com.stacc.backend.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in student's dashboard: short previews of their current LMS courses, upcoming assignments, recent
 * announcements, finalized attendance, and published results, with the full count beside each preview where one is
 * useful. Nothing is derived beyond what the owning features already send: no overall attendance, grade point
 * average, rank, pass or fail, or progress.
 */
public record StudentDashboardResponse(
        @Schema(description = "How many LMS courses the student can use now, under the same rule as their course list.",
                example = "4")
        int courseCount,

        @Schema(description = "The first of those courses, in course code order, at most "
                + StudentDashboardQueryService.COURSE_PREVIEW_LIMIT + ".")
        List<Course> courses,

        @Schema(description = "How many published assignments of those courses are due now or later.", example = "8")
        long upcomingAssignmentCount,

        @Schema(description = "The nearest of those deadlines first, at most "
                + StudentDashboardQueryService.ASSIGNMENT_PREVIEW_LIMIT + ".")
        List<UpcomingAssignment> upcomingAssignments,

        @Schema(description = "Published announcements of those courses, newest first, at most "
                + StudentDashboardQueryService.ANNOUNCEMENT_PREVIEW_LIMIT + ".")
        List<RecentAnnouncement> recentAnnouncements,

        @Schema(description = "How many course offerings have finalized attendance for the student.", example = "5")
        int attendanceCourseCount,

        @Schema(description = "The first of those course offerings, in course code order, at most "
                + StudentDashboardQueryService.ATTENDANCE_PREVIEW_LIMIT + ". Each has at least one finalized session.")
        List<AttendanceCourse> attendance,

        @Schema(description = "How many published results the student has.", example = "6")
        int resultCount,

        @Schema(description = "The most recently published results first, at most "
                + StudentDashboardQueryService.RESULT_PREVIEW_LIMIT + ".")
        List<RecentResult> recentResults) {

    public record Course(
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH") String programCode,
            @Schema(example = "2026-27") String academicSessionCode) {
    }

    public record UpcomingAssignment(
            @Schema(example = "7") Long assignmentId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Linked list exercises") String title,
            @Schema(description = "An absolute moment in UTC.", example = "2026-10-30T18:29:00Z") Instant dueAt,
            @Schema(example = "20.00") BigDecimal maxMarks) {
    }

    public record RecentAnnouncement(
            @Schema(example = "11") Long announcementId,
            @Schema(example = "42") Long lmsCourseId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "Lab moved to Thursday") String title,
            @Schema(example = "2026-10-18T09:00:00Z") Instant publishedAt) {
    }

    public record AttendanceCourse(
            @Schema(example = "17") Long courseOfferingId,
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH") String programCode,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "10") int totalSessions,
            @Schema(example = "9") int presentCount,
            @Schema(example = "1") int absentCount,
            @Schema(description = "As the attendance page sends it: presentCount / totalSessions * 100, two decimals.",
                    example = "90.00") BigDecimal attendancePercentage) {
    }

    public record RecentResult(
            @Schema(example = "BCS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH") String programCode,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "82.00") BigDecimal marksObtained,
            @Schema(example = "100.00") BigDecimal maxMarks,
            @Schema(description = "The official grade code, or null when none was entered.", example = "A") String gradeCode,
            @Schema(example = "2026-10-18T09:00:00Z") Instant publishedAt) {
    }
}
