package com.stacc.backend.dashboard;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository.SessionRecordCounts;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.dashboard.FacultyDashboardResponse.OpenAttendanceSession;
import com.stacc.backend.dashboard.FacultyDashboardResponse.PendingSubmission;
import com.stacc.backend.dashboard.FacultyDashboardResponse.TeachingCourse;
import com.stacc.backend.dashboard.FacultyDashboardResponse.UpcomingAssignment;
import com.stacc.backend.lms.access.LmsFacultyAccessPolicy;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseResponse;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the signed-in faculty member's dashboard from the features that own the data, in a fixed number of queries.
 * The offerings come from {@link LmsFacultyManagedCourseQueryService}, the same active-teaching-assignment rule as the
 * Teaching page, so offerings without an LMS course and archived ones are listed too. Assignment and grading queries
 * are limited to the LMS courses of those offerings that faculty can still open ({@link LmsFacultyAccessPolicy}: draft
 * or published), and attendance to the offerings themselves, with no LMS course needed. It reads only, and stores
 * nothing.
 */
@Service
@Transactional(readOnly = true)
public class FacultyDashboardQueryService {

    static final int TEACHING_PREVIEW_LIMIT = 6;
    static final int ASSIGNMENT_PREVIEW_LIMIT = 5;
    static final int GRADING_PREVIEW_LIMIT = 5;
    static final int ATTENDANCE_PREVIEW_LIMIT = 5;

    private final LmsFacultyManagedCourseQueryService teachingCourses;
    private final LmsAssignmentRepository assignments;
    private final LmsSubmissionAttemptRepository attempts;
    private final AttendanceSessionRepository attendanceSessions;
    private final AttendanceRecordRepository attendanceRecords;
    private final Clock clock;

    public FacultyDashboardQueryService(LmsFacultyManagedCourseQueryService teachingCourses,
            LmsAssignmentRepository assignments, LmsSubmissionAttemptRepository attempts,
            AttendanceSessionRepository attendanceSessions, AttendanceRecordRepository attendanceRecords, Clock clock) {
        this.teachingCourses = teachingCourses;
        this.assignments = assignments;
        this.attempts = attempts;
        this.attendanceSessions = attendanceSessions;
        this.attendanceRecords = attendanceRecords;
        this.clock = clock;
    }

    /**
     * The dashboard of the faculty member behind this account. Without teaching assignments every section is empty;
     * that is an answer, not an error.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public FacultyDashboardResponse getDashboard(Long userAccountId) {
        List<LmsFacultyManagedCourseResponse> taught = teachingCourses.getManagedCourses(userAccountId);
        // One entry per offering, and per LMS course that faculty can still open. Archived LMS courses stay in the
        // teaching list but feed no assignment or grading section.
        Map<Long, LmsFacultyManagedCourseResponse> byOffering = new LinkedHashMap<>();
        Map<Long, LmsFacultyManagedCourseResponse> byOpenLmsCourse = new LinkedHashMap<>();
        for (LmsFacultyManagedCourseResponse course : taught) {
            byOffering.putIfAbsent(course.courseOfferingId(), course);
            if (course.lmsCourseId() != null
                    && LmsFacultyAccessPolicy.isOpenToFaculty(LmsCourseStatus.valueOf(course.lmsCourseStatus()))) {
                byOpenLmsCourse.putIfAbsent(course.lmsCourseId(), course);
            }
        }

        List<UpcomingAssignment> upcoming = List.of();
        long upcomingCount = 0;
        List<PendingSubmission> pending = List.of();
        long pendingCount = 0;
        // No IN list is ever sent empty: without an open LMS course these queries are not made at all.
        if (!byOpenLmsCourse.isEmpty()) {
            Page<LmsAssignment> page = assignments.findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(
                    byOpenLmsCourse.keySet(), LmsContentPolicy.IN_USE, clock.instant(),
                    PageRequest.of(0, ASSIGNMENT_PREVIEW_LIMIT, Sort.by("dueAt").ascending().and(Sort.by("id").ascending())));
            upcoming = page.getContent().stream()
                    .map(assignment -> upcomingOf(assignment, byOpenLmsCourse.get(assignment.getLmsCourse().getId())))
                    .toList();
            upcomingCount = page.getTotalElements();

            Page<LmsSubmissionAttempt> waiting = attempts.findLatestUngradedByLmsCourseIdIn(
                    byOpenLmsCourse.keySet(), PageRequest.of(0, GRADING_PREVIEW_LIMIT));
            pending = waiting.getContent().stream()
                    .map(attempt -> pendingOf(attempt,
                            byOpenLmsCourse.get(attempt.getSubmission().getAssignment().getLmsCourse().getId())))
                    .toList();
            pendingCount = waiting.getTotalElements();
        }

        List<OpenAttendanceSession> open = List.of();
        long openCount = 0;
        if (!byOffering.isEmpty()) {
            Page<AttendanceSession> sessions = attendanceSessions.findOpenByCourseOfferingIdIn(
                    byOffering.keySet(), PageRequest.of(0, ATTENDANCE_PREVIEW_LIMIT));
            Set<Long> sessionIds = sessions.getContent().stream().map(AttendanceSession::getId).collect(Collectors.toSet());
            Map<Long, SessionRecordCounts> counts = sessionIds.isEmpty() ? Map.of()
                    : attendanceRecords.countRecordsBySessionIdIn(sessionIds).stream()
                            .collect(Collectors.toMap(SessionRecordCounts::getSessionId, Function.identity()));
            open = sessions.getContent().stream()
                    .map(session -> openOf(session, byOffering.get(session.getCourseOffering().getId()), counts.get(session.getId())))
                    .toList();
            openCount = sessions.getTotalElements();
        }

        return new FacultyDashboardResponse(
                byOffering.size(),
                byOffering.values().stream().limit(TEACHING_PREVIEW_LIMIT).map(FacultyDashboardQueryService::teachingOf).toList(),
                upcomingCount,
                upcoming,
                pendingCount,
                pending,
                openCount,
                open);
    }

    private static TeachingCourse teachingOf(LmsFacultyManagedCourseResponse course) {
        return new TeachingCourse(course.courseOfferingId(), course.courseCode(), course.courseName(), course.credits(),
                course.semesterNumber(), course.programCode(), course.academicSessionCode(), course.lmsCourseId(),
                course.lmsCourseStatus());
    }

    // Course details come from the teaching list above, so no further query is made per row.
    private static UpcomingAssignment upcomingOf(LmsAssignment assignment, LmsFacultyManagedCourseResponse course) {
        return new UpcomingAssignment(assignment.getId(), course.lmsCourseId(), course.courseCode(), course.courseName(),
                assignment.getTitle(), assignment.getDueAt(), assignment.getMaxMarks(), assignment.getStatus().name());
    }

    private static PendingSubmission pendingOf(LmsSubmissionAttempt attempt, LmsFacultyManagedCourseResponse course) {
        LmsAssignmentSubmission submission = attempt.getSubmission();
        String studentLoginId = submission.getCourseEnrollment().getSemesterEnrollment().getStudentProfile()
                .getUserAccount().getLoginId();
        return new PendingSubmission(submission.getId(), submission.getAssignment().getId(), course.lmsCourseId(),
                studentLoginId, course.courseCode(), course.courseName(), submission.getAssignment().getTitle(),
                attempt.getId(), attempt.getAttemptNumber(), attempt.getSubmittedAt(), attempt.isLate());
    }

    private static OpenAttendanceSession openOf(AttendanceSession session, LmsFacultyManagedCourseResponse course,
            SessionRecordCounts counts) {
        long total = counts == null ? 0 : counts.getTotalRecords();
        long unmarked = counts == null || counts.getUnmarkedRecords() == null ? 0 : counts.getUnmarkedRecords();
        return new OpenAttendanceSession(session.getId(), course.courseOfferingId(), course.courseCode(), course.courseName(),
                session.getHeldAt(), session.getTopic(), total, unmarked);
    }
}
