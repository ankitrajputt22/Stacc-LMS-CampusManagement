package com.stacc.backend.report;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionStatus;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.result.CourseResultStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessPolicy;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseResponse;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.report.FacultyReports.FacultyAssignmentReport;
import com.stacc.backend.report.FacultyReports.FacultyAttendanceReport;
import com.stacc.backend.report.FacultyReports.FacultyCourseReport;
import com.stacc.backend.report.FacultyReports.FacultyEnrollmentReport;
import com.stacc.backend.report.FacultyReports.FacultyReportCourse;
import com.stacc.backend.report.FacultyReports.FacultyReportCourseSummary;
import com.stacc.backend.report.FacultyReports.FacultyResultReport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty reports, for the course offerings the signed-in faculty member actively teaches (an ACTIVE faculty profile
 * with an ACTIVE teaching assignment), with or without an LMS course. Counts come from grouped database queries, a
 * fixed number per request whatever the number of offerings or students. It reads only.
 */
@Service
@Transactional(readOnly = true)
public class FacultyReportQueryService {

    static final String NOT_FOUND = "Course report not found.";

    private final LmsFacultyManagedCourseQueryService taughtCourses;
    private final FacultyTeachingAccessService teachingAccess;
    private final CourseOfferingRepository offerings;
    private final LmsCourseRepository lmsCourses;
    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsAssignmentRepository assignments;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsSubmissionAttemptRepository attempts;
    private final AttendanceSessionRepository attendanceSessions;
    private final AttendanceRecordRepository attendanceRecords;
    private final CourseResultRepository results;

    public FacultyReportQueryService(LmsFacultyManagedCourseQueryService taughtCourses,
            FacultyTeachingAccessService teachingAccess, CourseOfferingRepository offerings, LmsCourseRepository lmsCourses,
            CourseEnrollmentRepository courseEnrollments, LmsAssignmentRepository assignments,
            LmsAssignmentSubmissionRepository submissions, LmsSubmissionAttemptRepository attempts,
            AttendanceSessionRepository attendanceSessions, AttendanceRecordRepository attendanceRecords,
            CourseResultRepository results) {
        this.taughtCourses = taughtCourses;
        this.teachingAccess = teachingAccess;
        this.offerings = offerings;
        this.lmsCourses = lmsCourses;
        this.courseEnrollments = courseEnrollments;
        this.assignments = assignments;
        this.submissions = submissions;
        this.attempts = attempts;
        this.attendanceSessions = attendanceSessions;
        this.attendanceRecords = attendanceRecords;
        this.results = results;
    }

    /**
     * Every offering the faculty member teaches, as the Teaching page lists them, with four counts each. Five queries in
     * all: the taught offerings, then one grouped count per kind of record over all of them.
     */
    public List<FacultyReportCourseSummary> myCourses(Long accountId) {
        List<LmsFacultyManagedCourseResponse> taught = taughtCourses.getManagedCourses(accountId);
        if (taught.isEmpty()) {
            return List.of();
        }
        Set<Long> offeringIds = taught.stream().map(LmsFacultyManagedCourseResponse::courseOfferingId)
                .collect(Collectors.toSet());
        Set<Long> openLmsCourseIds = taught.stream().filter(FacultyReportQueryService::hasOpenLmsCourse)
                .map(LmsFacultyManagedCourseResponse::lmsCourseId).collect(Collectors.toSet());

        Map<Long, Long> enrolled = courseEnrollments.countByOfferingAndStatus(offeringIds).stream()
                .filter(row -> row.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .collect(Collectors.toMap(CourseEnrollmentRepository.OfferingStatusCount::getCourseOfferingId,
                        CourseEnrollmentRepository.OfferingStatusCount::getTotal, Long::sum));
        Map<Long, Long> finalized = attendanceSessions.countByOfferingAndStatus(offeringIds).stream()
                .filter(row -> row.getStatus() == AttendanceSessionStatus.FINALIZED)
                .collect(Collectors.toMap(AttendanceSessionRepository.OfferingStatusCount::getCourseOfferingId,
                        AttendanceSessionRepository.OfferingStatusCount::getTotal, Long::sum));
        Map<Long, Long> published = results.countByOfferingAndStatus(offeringIds).stream()
                .filter(row -> row.getStatus() == CourseResultStatus.PUBLISHED)
                .collect(Collectors.toMap(CourseResultRepository.OfferingStatusCount::getCourseOfferingId,
                        CourseResultRepository.OfferingStatusCount::getTotal, Long::sum));
        // No IN list is ever sent empty: without an open LMS course this query is not made.
        Map<Long, Long> assignmentTotals = openLmsCourseIds.isEmpty() ? Map.of()
                : assignments.countByCourseAndStatus(openLmsCourseIds, LmsContentPolicy.IN_USE).stream()
                        .collect(Collectors.toMap(LmsAssignmentRepository.CourseStatusCount::getLmsCourseId,
                                LmsAssignmentRepository.CourseStatusCount::getTotal, Long::sum));

        return taught.stream().map(course -> new FacultyReportCourseSummary(course.courseOfferingId(),
                course.courseCode(), course.courseName(), course.credits(), course.semesterNumber(), course.programCode(),
                course.academicSessionCode(), course.lmsCourseId(), course.lmsCourseStatus(),
                enrolled.getOrDefault(course.courseOfferingId(), 0L),
                hasOpenLmsCourse(course) ? assignmentTotals.getOrDefault(course.lmsCourseId(), 0L) : null,
                finalized.getOrDefault(course.courseOfferingId(), 0L),
                published.getOrDefault(course.courseOfferingId(), 0L))).toList();
    }

    /**
     * The report of one offering. The faculty member must actively teach it ({@link FacultyTeachingAccessService});
     * another faculty member's offering and a missing one answer the same 404. Without an LMS course faculty can open,
     * the assignment section is empty; the rest of the report is official academic data and is always complete.
     */
    public FacultyCourseReport courseReport(Long accountId, Long courseOfferingId) {
        if (courseOfferingId == null || !teachingAccess.canTeachCourseOffering(accountId, courseOfferingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        CourseOffering offering = offerings.findWithContextById(courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        Set<Long> ids = Set.of(courseOfferingId);

        Map<CourseEnrollmentStatus, Long> enrollment = courseEnrollments.countByOfferingAndStatus(ids).stream()
                .collect(Collectors.toMap(CourseEnrollmentRepository.OfferingStatusCount::getStatus,
                        CourseEnrollmentRepository.OfferingStatusCount::getTotal, Long::sum));
        Map<AttendanceSessionStatus, Long> sessions = attendanceSessions.countByOfferingAndStatus(ids).stream()
                .collect(Collectors.toMap(AttendanceSessionRepository.OfferingStatusCount::getStatus,
                        AttendanceSessionRepository.OfferingStatusCount::getTotal, Long::sum));
        Map<AttendanceStatus, Long> records = attendanceRecords.countFinalizedRecordsByStatus(courseOfferingId).stream()
                .collect(Collectors.toMap(AttendanceRecordRepository.RecordStatusCount::getStatus,
                        AttendanceRecordRepository.RecordStatusCount::getTotal, Long::sum));
        Map<CourseResultStatus, Long> resultCounts = results.countByOfferingAndStatus(ids).stream()
                .collect(Collectors.toMap(CourseResultRepository.OfferingStatusCount::getStatus,
                        CourseResultRepository.OfferingStatusCount::getTotal, Long::sum));

        Semester semester = offering.getSemester();
        return new FacultyCourseReport(
                new FacultyReportCourse(offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                        offering.getCourse().getCredits(), semester.getSemesterNumber(), semester.getProgram().getCode(),
                        semester.getProgram().getName(), semester.getAcademicSession().getCode(),
                        offering.getStatus().name()),
                new FacultyEnrollmentReport(enrollment.getOrDefault(CourseEnrollmentStatus.ENROLLED, 0L),
                        enrollment.getOrDefault(CourseEnrollmentStatus.COMPLETED, 0L),
                        enrollment.getOrDefault(CourseEnrollmentStatus.WITHDRAWN, 0L)),
                assignmentReport(lmsCourses.findByCourseOfferingId(courseOfferingId)),
                // Only PRESENT and ABSENT are counted; an UNMARKED record is never counted as absent.
                new FacultyAttendanceReport(sessions.getOrDefault(AttendanceSessionStatus.OPEN, 0L),
                        sessions.getOrDefault(AttendanceSessionStatus.FINALIZED, 0L),
                        records.getOrDefault(AttendanceStatus.PRESENT, 0L),
                        records.getOrDefault(AttendanceStatus.ABSENT, 0L)),
                new FacultyResultReport(resultCounts.getOrDefault(CourseResultStatus.DRAFT, 0L),
                        resultCounts.getOrDefault(CourseResultStatus.PUBLISHED, 0L)));
    }

    /** Counts only in an LMS course faculty can use ({@link LmsFacultyAccessPolicy}: draft or published). */
    private FacultyAssignmentReport assignmentReport(Optional<LmsCourse> found) {
        if (found.isEmpty()) {
            return new FacultyAssignmentReport(false, null, null, 0, 0, 0, 0);
        }
        LmsCourse course = found.get();
        if (!LmsFacultyAccessPolicy.isOpenToFaculty(course.getStatus())) {
            return new FacultyAssignmentReport(false, course.getId(), course.getStatus().name(), 0, 0, 0, 0);
        }
        Map<LmsContentStatus, Long> byStatus = assignments.countByCourseAndStatus(Set.of(course.getId()),
                LmsContentPolicy.IN_USE).stream()
                .collect(Collectors.toMap(LmsAssignmentRepository.CourseStatusCount::getStatus,
                        LmsAssignmentRepository.CourseStatusCount::getTotal, Long::sum));
        return new FacultyAssignmentReport(true, course.getId(), course.getStatus().name(),
                byStatus.getOrDefault(LmsContentStatus.DRAFT, 0L), byStatus.getOrDefault(LmsContentStatus.PUBLISHED, 0L),
                submissions.countSubmittedForPublishedAssignments(course.getId()),
                attempts.countLatestUngradedByLmsCourseIdIn(Set.of(course.getId())));
    }

    private static boolean hasOpenLmsCourse(LmsFacultyManagedCourseResponse course) {
        return course.lmsCourseId() != null
                && LmsFacultyAccessPolicy.isOpenToFaculty(LmsCourseStatus.valueOf(course.lmsCourseStatus()));
    }
}
