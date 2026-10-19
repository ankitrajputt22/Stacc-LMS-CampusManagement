package com.stacc.backend.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.academic.attendance.api.StudentAttendanceQueryService;
import com.stacc.backend.academic.attendance.api.StudentAttendanceResponse;
import com.stacc.backend.academic.result.api.StudentResultQueryService;
import com.stacc.backend.academic.result.api.StudentResultsResponse;
import com.stacc.backend.dashboard.StudentDashboardResponse.AttendanceCourse;
import com.stacc.backend.dashboard.StudentDashboardResponse.Course;
import com.stacc.backend.dashboard.StudentDashboardResponse.RecentAnnouncement;
import com.stacc.backend.dashboard.StudentDashboardResponse.RecentResult;
import com.stacc.backend.dashboard.StudentDashboardResponse.UpcomingAssignment;
import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the signed-in student's dashboard from the features that own the data, in a fixed number of queries. The
 * courses come from {@link LmsStudentCourseQueryService}, the same rule as the student's course list, and only those
 * courses' published assignments and announcements are read, so no other LMS access rule exists here. Attendance and
 * results come from {@link StudentAttendanceQueryService} and {@link StudentResultQueryService}, which follow the
 * student's own course enrollments and need no LMS course: history stays visible without current LMS access. It
 * reads only, and stores nothing.
 */
@Service
@Transactional(readOnly = true)
public class StudentDashboardQueryService {

    static final int COURSE_PREVIEW_LIMIT = 6;
    static final int ASSIGNMENT_PREVIEW_LIMIT = 5;
    static final int ANNOUNCEMENT_PREVIEW_LIMIT = 5;
    static final int ATTENDANCE_PREVIEW_LIMIT = 4;
    static final int RESULT_PREVIEW_LIMIT = 5;

    // Latest publication first. Results carry no ID, so ties fall back to the academic context: the latest session,
    // the highest semester, then course code.
    private static final Comparator<StudentResultsResponse.Result> RECENT_FIRST = Comparator
            .comparing(StudentResultsResponse.Result::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(StudentResultsResponse.Result::academicSessionCode, Comparator.reverseOrder())
            .thenComparing(StudentResultsResponse.Result::semesterNumber, Comparator.reverseOrder())
            .thenComparing(StudentResultsResponse.Result::courseCode);

    private final LmsStudentCourseQueryService courseQueries;
    private final LmsAssignmentRepository assignments;
    private final LmsAnnouncementRepository announcements;
    private final StudentAttendanceQueryService attendanceQueries;
    private final StudentResultQueryService resultQueries;
    private final Clock clock;

    public StudentDashboardQueryService(LmsStudentCourseQueryService courseQueries, LmsAssignmentRepository assignments,
            LmsAnnouncementRepository announcements, StudentAttendanceQueryService attendanceQueries,
            StudentResultQueryService resultQueries, Clock clock) {
        this.courseQueries = courseQueries;
        this.assignments = assignments;
        this.announcements = announcements;
        this.attendanceQueries = attendanceQueries;
        this.resultQueries = resultQueries;
        this.clock = clock;
    }

    /**
     * The dashboard of the student behind this account. A student without data for a section gets an empty section,
     * never an error.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public StudentDashboardResponse getDashboard(Long userAccountId) {
        Map<Long, LmsCourseSummaryResponse> courses = new LinkedHashMap<>();
        for (LmsCourseSummaryResponse course : courseQueries.getAccessibleCourses(userAccountId)) {
            courses.put(course.lmsCourseId(), course);
        }

        List<UpcomingAssignment> upcoming = List.of();
        long upcomingCount = 0;
        List<RecentAnnouncement> recentAnnouncements = List.of();
        // Without a current course there is nothing to look up, and no empty IN list is ever sent.
        if (!courses.isEmpty()) {
            Instant now = clock.instant();
            Page<LmsAssignment> page = assignments.findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(
                    courses.keySet(), LmsContentStatus.PUBLISHED, now,
                    PageRequest.of(0, ASSIGNMENT_PREVIEW_LIMIT, Sort.by("dueAt").ascending().and(Sort.by("id").ascending())));
            upcoming = page.getContent().stream()
                    .map(assignment -> upcomingOf(assignment, courses.get(assignment.getLmsCourse().getId())))
                    .toList();
            upcomingCount = page.getTotalElements();
            recentAnnouncements = announcements.findByLmsCourseIdInAndStatus(courses.keySet(), LmsContentStatus.PUBLISHED,
                            PageRequest.of(0, ANNOUNCEMENT_PREVIEW_LIMIT,
                                    Sort.by("publishedAt").descending().and(Sort.by("id").descending())))
                    .stream()
                    .map(announcement -> announcementOf(announcement, courses.get(announcement.getLmsCourse().getId())))
                    .toList();
        }

        List<StudentAttendanceResponse.Course> attendance = attendanceQueries.getMyAttendance(userAccountId).courses();
        List<StudentResultsResponse.Result> results = resultQueries.getMyResults(userAccountId).results();

        return new StudentDashboardResponse(
                courses.size(),
                courses.values().stream().limit(COURSE_PREVIEW_LIMIT).map(StudentDashboardQueryService::courseOf).toList(),
                upcomingCount,
                upcoming,
                recentAnnouncements,
                attendance.size(),
                attendance.stream().limit(ATTENDANCE_PREVIEW_LIMIT).map(StudentDashboardQueryService::attendanceOf).toList(),
                results.size(),
                results.stream().sorted(RECENT_FIRST).limit(RESULT_PREVIEW_LIMIT).map(StudentDashboardQueryService::resultOf).toList());
    }

    private static Course courseOf(LmsCourseSummaryResponse course) {
        return new Course(course.lmsCourseId(), course.courseCode(), course.courseName(), course.credits(),
                course.semesterNumber(), course.programCode(), course.academicSessionCode());
    }

    // The course details come from the course list above, so no further query is made per row.
    private static UpcomingAssignment upcomingOf(LmsAssignment assignment, LmsCourseSummaryResponse course) {
        return new UpcomingAssignment(assignment.getId(), course.lmsCourseId(), course.courseCode(), course.courseName(),
                assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks());
    }

    private static RecentAnnouncement announcementOf(LmsAnnouncement announcement, LmsCourseSummaryResponse course) {
        return new RecentAnnouncement(announcement.getId(), course.lmsCourseId(), course.courseCode(), course.courseName(),
                announcement.getTitle(), announcement.getPublishedAt());
    }

    private static AttendanceCourse attendanceOf(StudentAttendanceResponse.Course course) {
        return new AttendanceCourse(course.courseOfferingId(), course.courseCode(), course.courseName(),
                course.semesterNumber(), course.programCode(), course.academicSessionCode(), course.totalSessions(),
                course.presentCount(), course.absentCount(), course.attendancePercentage());
    }

    private static RecentResult resultOf(StudentResultsResponse.Result result) {
        return new RecentResult(result.courseCode(), result.courseName(), result.credits(), result.semesterNumber(),
                result.programCode(), result.academicSessionCode(), result.marksObtained(), result.maxMarks(),
                result.gradeCode(), result.publishedAt());
    }
}
