package com.stacc.backend.academic.attendance.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

/** Student A is account 11. The repository answers as the database query would, latest class first. */
class StudentAttendanceQueryServiceTest {

    private static final Long STUDENT_A = 11L;

    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final StudentAttendanceQueryService service = new StudentAttendanceQueryService(records);

    private final FacultyProfile faculty = AttendanceFixtures.faculty(21, 61);
    private final CourseOffering systems = AttendanceFixtures.offering(18, "BCS401", "Operating Systems");
    private final CourseOffering structures = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final CourseEnrollment inSystems = AttendanceFixtures.enrollment(systems, STUDENT_A, 40, "2408400100011");
    private final CourseEnrollment inStructures = AttendanceFixtures.enrollment(structures, STUDENT_A, 41, "2408400100011");

    private long nextId = 100;

    /** A record of a session of the offering, held on the given day of October 2026, marked and finalized. */
    private AttendanceRecord finalized(CourseEnrollment enrollment, int day, AttendanceStatus status) {
        AttendanceRecord record = open(enrollment, day, status);
        record.getAttendanceSession().markFinalized(Instant.parse("2026-10-30T10:00:00Z"));
        return record;
    }

    private AttendanceRecord open(CourseEnrollment enrollment, int day, AttendanceStatus status) {
        long id = nextId++;
        AttendanceSession session = AttendanceFixtures.session(enrollment.getCourseOffering(), faculty, id,
                "2026-10-%02dT04:30:00Z".formatted(day));
        AttendanceRecord record = AttendanceFixtures.record(session, enrollment, id + 1000);
        record.mark(status, faculty, Instant.parse("2026-10-%02dT05:00:00Z".formatted(day)));
        return record;
    }

    @Test
    void tenSessionsWithEightPresentAreEightyPercent() {
        List<AttendanceRecord> answer = new ArrayList<>();
        for (int day = 20; day > 10; day--) {
            answer.add(finalized(inStructures, day, day <= 12 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT));
        }
        when(records.findAllFinalizedByUserAccountId(STUDENT_A)).thenReturn(answer);

        StudentAttendanceResponse.Course course = service.getMyAttendance(STUDENT_A).courses().getFirst();

        assertEquals(10, course.totalSessions());
        assertEquals(8, course.presentCount());
        assertEquals(2, course.absentCount());
        assertEquals(new BigDecimal("80.00"), course.attendancePercentage());
        assertEquals(Instant.parse("2026-10-20T04:30:00Z"), course.sessions().getFirst().heldAt());
        assertEquals("ABSENT", course.sessions().getLast().status());
    }

    @Test
    void percentagesAreRoundedHalfUpToTwoPlaces() {
        assertEquals(new BigDecimal("83.33"), StudentAttendanceQueryService.percentage(5, 6));
        assertEquals(new BigDecimal("66.67"), StudentAttendanceQueryService.percentage(2, 3));
        assertEquals(new BigDecimal("100.00"), StudentAttendanceQueryService.percentage(3, 3));
        assertEquals(new BigDecimal("0.00"), StudentAttendanceQueryService.percentage(0, 4));
        assertNull(StudentAttendanceQueryService.percentage(0, 0));
    }

    @Test
    void coursesAreGroupedInCourseCodeOrderWithTheirOwnDetails() {
        when(records.findAllFinalizedByUserAccountId(STUDENT_A)).thenReturn(List.of(
                finalized(inSystems, 19, AttendanceStatus.PRESENT),
                finalized(inStructures, 18, AttendanceStatus.ABSENT),
                finalized(inStructures, 12, AttendanceStatus.PRESENT)));

        List<StudentAttendanceResponse.Course> courses = service.getMyAttendance(STUDENT_A).courses();

        assertEquals(List.of("BCS301", "BCS401"), courses.stream().map(StudentAttendanceResponse.Course::courseCode).toList());
        StudentAttendanceResponse.Course structuresCourse = courses.getFirst();
        assertEquals("Data Structures", structuresCourse.courseName());
        assertEquals(3, structuresCourse.semesterNumber());
        assertEquals("BTECH", structuresCourse.programCode());
        assertEquals("2026-27", structuresCourse.academicSessionCode());
        assertEquals(new BigDecimal("50.00"), structuresCourse.attendancePercentage());
        assertEquals(List.of("ABSENT", "PRESENT"),
                structuresCourse.sessions().stream().map(StudentAttendanceResponse.Session::status).toList());
    }

    @Test
    void anOpenSessionNeverReachesTheStudentAndACourseWithoutFinalizedAttendanceIsNotListed() {
        when(records.findAllFinalizedByUserAccountId(STUDENT_A)).thenReturn(List.of(
                open(inSystems, 19, AttendanceStatus.PRESENT),
                finalized(inStructures, 18, AttendanceStatus.PRESENT),
                open(inStructures, 20, AttendanceStatus.ABSENT)));

        List<StudentAttendanceResponse.Course> courses = service.getMyAttendance(STUDENT_A).courses();

        assertEquals(1, courses.size());
        assertEquals(1, courses.getFirst().totalSessions());
        assertEquals(new BigDecimal("100.00"), courses.getFirst().attendancePercentage());
    }

    @Test
    void attendanceStaysAfterTheEnrollmentEnds() {
        AttendanceRecord record = finalized(inStructures, 18, AttendanceStatus.PRESENT);
        inStructures.withdraw();
        when(records.findAllFinalizedByUserAccountId(STUDENT_A)).thenReturn(List.of(record));

        assertEquals(1, service.getMyAttendance(STUDENT_A).courses().getFirst().presentCount());
    }

    @Test
    void noAttendanceMeansNoCoursesAndNoPercentage() {
        when(records.findAllFinalizedByUserAccountId(STUDENT_A)).thenReturn(List.of());

        assertEquals(List.of(), service.getMyAttendance(STUDENT_A).courses());
        assertEquals(List.of(), service.getMyAttendance(null).courses());
        verify(records, never()).findAllFinalizedByUserAccountId(null);
    }
}
