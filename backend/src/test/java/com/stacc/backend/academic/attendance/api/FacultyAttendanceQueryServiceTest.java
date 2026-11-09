package com.stacc.backend.academic.attendance.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Faculty A (account 21) teaches offerings 17 and 18, neither with an LMS course; faculty B (22) teaches neither. */
class FacultyAttendanceQueryServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final FacultyAttendanceQueryService service = new FacultyAttendanceQueryService(
            teachingAssignments, new FacultyTeachingAccessService(teachingAssignments), sessions, records);

    private final CourseOffering structures = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final CourseOffering systems = AttendanceFixtures.offering(18, "BCS401", "Operating Systems");
    private final CourseOffering networks = AttendanceFixtures.offering(19, "BCS501", "Computer Networks");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(FACULTY_A, 61);
    private final TeachingAssignment teachingStructures = AttendanceFixtures.teaching(facultyA, structures);
    private final TeachingAssignment teachingSystems = AttendanceFixtures.teaching(facultyA, systems);
    private final TeachingAssignment endedNetworks = AttendanceFixtures.teaching(facultyA, networks);
    private final AttendanceSession session = AttendanceFixtures.session(structures, facultyA, 8, "2026-10-19T04:30:00Z");

    @BeforeEach
    void setUp() {
        endedNetworks.deactivate();
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(anyLong(), anyLong())).thenReturn(List.of());
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, 17L)).thenReturn(List.of(teachingStructures));
        when(sessions.findWithCourseById(8L)).thenReturn(Optional.of(session));
    }

    @Test
    void myCoursesAreTheOfferingsTaughtNowInCourseCodeOrder() {
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY_A))
                .thenReturn(List.of(teachingSystems, endedNetworks, teachingStructures, teachingStructures));

        List<AttendanceCourseOfferingResponse> courses = service.getMyCourseOfferings(FACULTY_A);

        assertEquals(List.of(17L, 18L), courses.stream().map(AttendanceCourseOfferingResponse::courseOfferingId).toList());
        AttendanceCourseOfferingResponse first = courses.getFirst();
        assertEquals("BCS301", first.courseCode());
        assertEquals("Data Structures", first.courseName());
        assertEquals(3, first.semesterNumber());
        assertEquals("BTECH", first.programCode());
        assertEquals("2026-27", first.academicSessionCode());
    }

    @Test
    void anInactiveProfileListsNothing() {
        facultyA.deactivate();
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(FACULTY_A)).thenReturn(List.of(teachingStructures));

        assertEquals(List.of(), service.getMyCourseOfferings(FACULTY_A));
    }

    @Test
    void sessionsAreListedOnlyForAnOfferingTaughtNow() {
        AttendanceSession older = AttendanceFixtures.session(structures, facultyA, 7, "2026-10-12T04:30:00Z");
        when(sessions.findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(17L)).thenReturn(List.of(session, older));

        assertEquals(List.of(8L, 7L), service.getSessions(FACULTY_A, 17L).stream().map(AttendanceSessionResponse::id).toList());

        ApiException refused = assertThrows(ApiException.class, () -> service.getSessions(FACULTY_B, 17L));
        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(FacultyAttendanceQueryService.COURSE_UNAVAILABLE, refused.getMessage());
        verify(sessions, never()).findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(18L);
    }

    @Test
    void aSessionShowsItsRosterByStudentIdOnly() {
        AttendanceRecord a = AttendanceFixtures.record(session, AttendanceFixtures.enrollment(structures, 11, 40, "2408400100011"), 101);
        AttendanceRecord b = AttendanceFixtures.record(session, AttendanceFixtures.enrollment(structures, 12, 41, "2408400100012"), 102);
        a.mark(AttendanceStatus.PRESENT, facultyA, Instant.parse("2026-10-19T05:00:00Z"));
        when(records.findAllWithStudentBySessionId(8L)).thenReturn(List.of(a, b));

        AttendanceSessionDetailResponse detail = service.getSessionDetail(FACULTY_A, 8L);

        assertEquals(8L, detail.sessionId());
        assertEquals(17L, detail.courseOfferingId());
        assertEquals("BCS301", detail.courseCode());
        assertEquals("OPEN", detail.status());
        assertEquals(List.of(
                new AttendanceRecordResponse(101L, "2408400100011", "PRESENT", Instant.parse("2026-10-19T05:00:00Z")),
                new AttendanceRecordResponse(102L, "2408400100012", "UNMARKED", null)), detail.records());
    }

    @Test
    void aSessionOfAnOfferingNotTaughtOrMissingAnswersTheSame404() {
        for (Long sessionId : new Long[] {8L, 99L}) {
            Long account = sessionId == 8L ? FACULTY_B : FACULTY_A;
            ApiException refused = assertThrows(ApiException.class, () -> service.getSessionDetail(account, sessionId));
            assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
            assertEquals(FacultyAttendanceQueryService.SESSION_UNAVAILABLE, refused.getMessage());
        }
        verify(records, never()).findAllWithStudentBySessionId(anyLong());
    }
}
