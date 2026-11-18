package com.stacc.backend.academic.attendance.api;

import java.util.Comparator;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FacultyAttendanceQueryService {

    static final String COURSE_UNAVAILABLE = "Course offering not found or unavailable.";
    static final String SESSION_UNAVAILABLE = "Attendance session not found or unavailable.";

    private static final Comparator<AttendanceCourseOfferingResponse> BY_COURSE_CODE = Comparator
            .comparing(AttendanceCourseOfferingResponse::courseCode)
            .thenComparing(AttendanceCourseOfferingResponse::courseOfferingId);

    private final TeachingAssignmentRepository teachingAssignments;
    private final FacultyTeachingAccessService teachingAccess;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;

    public FacultyAttendanceQueryService(
            TeachingAssignmentRepository teachingAssignments,
            FacultyTeachingAccessService teachingAccess,
            AttendanceSessionRepository sessions,
            AttendanceRecordRepository records) {
        this.teachingAssignments = teachingAssignments;
        this.teachingAccess = teachingAccess;
        this.sessions = sessions;
        this.records = records;
    }

    public List<AttendanceCourseOfferingResponse> getMyCourseOfferings(Long userAccountId) {
        if (userAccountId == null) {
            return List.of();
        }
        return teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId).stream()
                .filter(assignment -> FacultyTeachingAccessService.isCurrentTeaching(assignment, userAccountId))
                .map(assignment -> summaryOf(assignment.getCourseOffering()))
                .distinct()
                .sorted(BY_COURSE_CODE)
                .toList();
    }

    public List<AttendanceSessionResponse> getSessions(Long userAccountId, Long courseOfferingId) {
        if (!teachingAccess.canTeachCourseOffering(userAccountId, courseOfferingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        return sessions.findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(courseOfferingId).stream()
                .map(AttendanceSessionResponse::of)
                .toList();
    }

    public AttendanceSessionDetailResponse getSessionDetail(Long userAccountId, Long sessionId) {
        AttendanceSession session = (sessionId == null ? null : sessions.findWithCourseById(sessionId).orElse(null));
        if (session == null || !teachingAccess.canTeachCourseOffering(userAccountId, session.getCourseOffering().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, SESSION_UNAVAILABLE);
        }
        Course course = session.getCourseOffering().getCourse();
        List<AttendanceRecordResponse> roster = records.findAllWithStudentBySessionId(session.getId()).stream()
                .map(FacultyAttendanceQueryService::recordOf)
                .toList();
        return new AttendanceSessionDetailResponse(
                session.getId(),
                session.getCourseOffering().getId(),
                course.getCode(),
                course.getName(),
                session.getHeldAt(),
                session.getTopic(),
                session.getStatus().name(),
                session.getFinalizedAt(),
                roster);
    }

    private static AttendanceRecordResponse recordOf(AttendanceRecord record) {
        String loginId = record.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId();
        return new AttendanceRecordResponse(record.getId(), loginId, record.getStatus().name(), record.getMarkedAt());
    }

    static AttendanceCourseOfferingResponse summaryOf(CourseOffering courseOffering) {
        Course course = courseOffering.getCourse();
        Semester semester = courseOffering.getSemester();
        return new AttendanceCourseOfferingResponse(
                courseOffering.getId(),
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode());
    }
}
