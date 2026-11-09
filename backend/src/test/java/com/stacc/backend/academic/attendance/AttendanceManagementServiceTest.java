package com.stacc.backend.academic.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.stacc.backend.academic.attendance.AttendanceManagementService.RecordChange;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Course offering 17 has no LMS course. Faculty A (account 21) and co-teacher C (account 23) teach it; faculty B
 * (account 22) teaches nothing. Students A and B are enrolled, student C has withdrawn. The real teaching access
 * service decides over stand-in repositories, and the stand-ins keep what is saved, as the database would.
 */
class AttendanceManagementServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;
    private static final Long FACULTY_C = 23L;
    private static final Long OFFERING = 17L;
    private static final Instant HELD = Instant.parse("2026-10-19T04:30:00Z");
    private static final Instant NOW = Instant.parse("2026-10-19T06:00:00.123456789Z");
    private static final Instant NOW_STORED = Instant.parse("2026-10-19T06:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-22T09:15:00Z");

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final CourseEnrollmentRepository courseEnrollments = mock(CourseEnrollmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private final CourseOffering offering = AttendanceFixtures.offering(OFFERING, "BCS301", "Data Structures");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(FACULTY_A, 61);
    private final FacultyProfile facultyC = AttendanceFixtures.faculty(FACULTY_C, 63);
    private final TeachingAssignment teachingA = AttendanceFixtures.teaching(facultyA, offering);
    private final TeachingAssignment teachingC = AttendanceFixtures.teaching(facultyC, offering);
    private final CourseEnrollment studentA = AttendanceFixtures.enrollment(offering, 11, 40, "2408400100011");
    private final CourseEnrollment studentB = AttendanceFixtures.enrollment(offering, 12, 41, "2408400100012");
    private final CourseEnrollment studentC = AttendanceFixtures.enrollment(offering, 13, 42, "2408400100013");
    private final List<CourseEnrollment> enrollmentsOfOffering = new ArrayList<>(List.of(studentA, studentB, studentC));

    private final Map<Long, AttendanceSession> storedSessions = new HashMap<>();
    private final Map<Long, List<AttendanceRecord>> storedRecords = new HashMap<>();
    private final AtomicLong nextSessionId = new AtomicLong(500);
    private final AtomicLong nextRecordId = new AtomicLong(900);

    private AttendanceManagementService serviceAt(Instant now) {
        return new AttendanceManagementService(sessions, records, courseEnrollments,
                new FacultyTeachingAccessService(teachingAssignments), Clock.fixed(now, ZoneOffset.UTC), notifications);
    }

    @BeforeEach
    void setUp() {
        studentC.withdraw();
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(anyLong(), anyLong())).thenReturn(List.of());
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, OFFERING)).thenReturn(List.of(teachingA));
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_C, OFFERING)).thenReturn(List.of(teachingC));
        // As the database query: only the offering's enrollments that are enrolled right now.
        when(courseEnrollments.findAllByCourseOfferingIdAndStatus(OFFERING, CourseEnrollmentStatus.ENROLLED))
                .thenAnswer(call -> enrollmentsOfOffering.stream()
                        .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED).toList());
        when(sessions.save(any(AttendanceSession.class))).thenAnswer(call -> {
            AttendanceSession session = call.getArgument(0);
            ReflectionTestUtils.setField(session, "id", nextSessionId.getAndIncrement());
            storedSessions.put(session.getId(), session);
            return session;
        });
        when(sessions.saveAndFlush(any(AttendanceSession.class))).thenAnswer(call -> call.getArgument(0));
        when(sessions.findByIdForUpdate(anyLong()))
                .thenAnswer(call -> Optional.ofNullable(storedSessions.get(call.<Long>getArgument(0))));
        when(records.saveAll(any())).thenAnswer(call -> {
            List<AttendanceRecord> saved = new ArrayList<>();
            for (AttendanceRecord record : call.<Iterable<AttendanceRecord>>getArgument(0)) {
                ReflectionTestUtils.setField(record, "id", nextRecordId.getAndIncrement());
                storedRecords.computeIfAbsent(record.getAttendanceSession().getId(), id -> new ArrayList<>()).add(record);
                saved.add(record);
            }
            return saved;
        });
        when(records.findAllWithStudentBySessionId(anyLong()))
                .thenAnswer(call -> List.copyOf(storedRecords.getOrDefault(call.<Long>getArgument(0), List.of())));
        when(records.countByAttendanceSessionIdAndStatus(anyLong(), any())).thenAnswer(call -> storedRecords
                .getOrDefault(call.<Long>getArgument(0), List.of()).stream()
                .filter(record -> record.getStatus() == call.getArgument(1)).count());
    }

    private AttendanceSession createdSession() {
        return serviceAt(NOW).createSession(FACULTY_A, OFFERING, HELD, "Arrays introduction");
    }

    private List<AttendanceRecord> rosterOf(AttendanceSession session) {
        return storedRecords.getOrDefault(session.getId(), List.of());
    }

    private AttendanceRecord recordOf(AttendanceSession session, CourseEnrollment enrollment) {
        return rosterOf(session).stream().filter(record -> record.getCourseEnrollment() == enrollment).findFirst().orElseThrow();
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void creatingASessionSnapshotsOnlyTheStudentsEnrolledNowAllUnmarked() {
        AttendanceSession session = createdSession();

        assertEquals(AttendanceSessionStatus.OPEN, session.getStatus());
        assertSame(offering, session.getCourseOffering());
        assertSame(facultyA, session.getCreatedByFacultyProfile());
        assertEquals(HELD, session.getHeldAt());
        assertEquals("Arrays introduction", session.getTopic());
        assertEquals(List.of(studentA, studentB), rosterOf(session).stream().map(AttendanceRecord::getCourseEnrollment).toList());
        assertTrue(rosterOf(session).stream().allMatch(record -> record.getStatus() == AttendanceStatus.UNMARKED
                && record.getMarkedByFacultyProfile() == null && record.getMarkedAt() == null));
    }

    @Test
    void aLaterWithdrawalKeepsTheStudentsRecord() {
        AttendanceSession session = createdSession();
        studentA.withdraw();

        assertEquals(2, rosterOf(session).size());
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(
                new RecordChange(recordOf(session, studentA).getId(), AttendanceStatus.PRESENT),
                new RecordChange(recordOf(session, studentB).getId(), AttendanceStatus.ABSENT)));
        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());
        assertEquals(AttendanceStatus.PRESENT, recordOf(session, studentA).getStatus());
        verify(records, never()).delete(any());
        verify(records, never()).deleteAll(any());
    }

    @Test
    void aNewEnrollmentJoinsOnlyLaterSessions() {
        AttendanceSession monday = createdSession();
        CourseEnrollment studentD = AttendanceFixtures.enrollment(offering, 14, 43, "2408400100014");
        enrollmentsOfOffering.add(studentD);

        AttendanceSession tuesday = serviceAt(NOW).createSession(FACULTY_A, OFFERING, HELD.plusSeconds(86_400), null);

        assertEquals(List.of(studentA, studentB), rosterOf(monday).stream().map(AttendanceRecord::getCourseEnrollment).toList());
        assertEquals(List.of(studentA, studentB, studentD),
                rosterOf(tuesday).stream().map(AttendanceRecord::getCourseEnrollment).toList());
    }

    @Test
    void onlyFacultyWhoTeachTheOfferingNowCanCreateASession() {
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.COURSE_UNAVAILABLE,
                () -> serviceAt(NOW).createSession(FACULTY_B, OFFERING, HELD, null));
        facultyA.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.COURSE_UNAVAILABLE, this::createdSession);
        facultyA.activate();
        teachingA.deactivate();
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.COURSE_UNAVAILABLE, this::createdSession);
        verify(sessions, never()).save(any());
        verify(records, never()).saveAll(any());
    }

    @Test
    void anOpenSessionCanBeSavedPartOfTheWay() {
        AttendanceSession session = createdSession();

        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(),
                List.of(new RecordChange(recordOf(session, studentA).getId(), AttendanceStatus.PRESENT)));

        AttendanceRecord a = recordOf(session, studentA);
        assertEquals(AttendanceStatus.PRESENT, a.getStatus());
        assertSame(facultyA, a.getMarkedByFacultyProfile());
        assertEquals(NOW_STORED, a.getMarkedAt());
        assertEquals(AttendanceStatus.UNMARKED, recordOf(session, studentB).getStatus());
        assertEquals(AttendanceSessionStatus.OPEN, session.getStatus());
    }

    @Test
    void anOpenSessionsRecordCanBeUnmarkedAgain() {
        AttendanceSession session = createdSession();
        Long a = recordOf(session, studentA).getId();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(new RecordChange(a, AttendanceStatus.ABSENT)));

        serviceAt(LATER).saveRecords(FACULTY_A, session.getId(), List.of(new RecordChange(a, AttendanceStatus.UNMARKED)));

        assertEquals(AttendanceStatus.UNMARKED, recordOf(session, studentA).getStatus());
        assertNull(recordOf(session, studentA).getMarkedAt());
    }

    @Test
    void recordsOfAnotherSessionOrSentTwiceAreRefusedAndNothingChanges() {
        AttendanceSession session = createdSession();
        AttendanceSession other = serviceAt(NOW).createSession(FACULTY_A, OFFERING, LATER, null);
        Long a = recordOf(session, studentA).getId();
        Long foreign = recordOf(other, studentB).getId();

        assertRefused(HttpStatus.BAD_REQUEST, AttendanceManagementService.FOREIGN_RECORD,
                () -> serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(
                        new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(foreign, AttendanceStatus.PRESENT))));
        assertRefused(HttpStatus.BAD_REQUEST, AttendanceManagementService.FOREIGN_RECORD,
                () -> serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(new RecordChange(99_999L, AttendanceStatus.PRESENT))));
        assertRefused(HttpStatus.BAD_REQUEST, AttendanceManagementService.DUPLICATE_RECORD,
                () -> serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(
                        new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(a, AttendanceStatus.ABSENT))));

        assertEquals(AttendanceStatus.UNMARKED, recordOf(session, studentA).getStatus());
        assertEquals(AttendanceStatus.UNMARKED, recordOf(other, studentB).getStatus());
    }

    @Test
    void facultyWhoDoNotTeachTheOfferingCannotTouchAGuessedSession() {
        AttendanceSession session = createdSession();
        Long a = recordOf(session, studentA).getId();

        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.SESSION_UNAVAILABLE,
                () -> serviceAt(NOW).saveRecords(FACULTY_B, session.getId(), List.of(new RecordChange(a, AttendanceStatus.ABSENT))));
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.SESSION_UNAVAILABLE,
                () -> serviceAt(NOW).finalizeSession(FACULTY_B, session.getId()));
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.SESSION_UNAVAILABLE,
                () -> serviceAt(NOW).updateSession(FACULTY_B, session.getId(), LATER, "Changed"));
        assertRefused(HttpStatus.NOT_FOUND, AttendanceManagementService.SESSION_UNAVAILABLE,
                () -> serviceAt(NOW).finalizeSession(FACULTY_A, 99_999L));

        assertEquals(AttendanceStatus.UNMARKED, recordOf(session, studentA).getStatus());
        assertEquals("Arrays introduction", session.getTopic());
    }

    @Test
    void theSessionIsLockedBeforeAccessIsCheckedAndRecordsAreRead() {
        AttendanceSession session = createdSession();

        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of());

        InOrder order = inOrder(sessions, teachingAssignments, records);
        order.verify(sessions).findByIdForUpdate(session.getId());
        order.verify(teachingAssignments).findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, OFFERING);
        order.verify(records).findAllWithStudentBySessionId(session.getId());
    }

    @Test
    void finalizingNeedsEveryStudentMarked() {
        AttendanceSession session = createdSession();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(),
                List.of(new RecordChange(recordOf(session, studentA).getId(), AttendanceStatus.PRESENT)));

        assertRefused(HttpStatus.BAD_REQUEST, AttendanceManagementService.NOT_ALL_MARKED,
                () -> serviceAt(NOW).finalizeSession(FACULTY_A, session.getId()));
        assertEquals(AttendanceSessionStatus.OPEN, session.getStatus());
        assertNull(session.getFinalizedAt());
    }

    @Test
    void finalizingAMarkedSessionIsKeptOnceAndRepeatingItChangesNothing() {
        AttendanceSession session = createdSession();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(
                new RecordChange(recordOf(session, studentA).getId(), AttendanceStatus.PRESENT),
                new RecordChange(recordOf(session, studentB).getId(), AttendanceStatus.ABSENT)));

        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());
        AttendanceSession again = serviceAt(LATER).finalizeSession(FACULTY_C, session.getId());

        assertEquals(AttendanceSessionStatus.FINALIZED, again.getStatus());
        assertEquals(NOW_STORED, again.getFinalizedAt());
    }

    @Test
    void finalizedAttendanceCanBeCorrectedBetweenPresentAndAbsentByAnyCurrentTeacher() {
        AttendanceSession session = createdSession();
        Long a = recordOf(session, studentA).getId();
        Long b = recordOf(session, studentB).getId();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(),
                List.of(new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(b, AttendanceStatus.ABSENT)));
        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());

        serviceAt(LATER).saveRecords(FACULTY_C, session.getId(),
                List.of(new RecordChange(a, AttendanceStatus.ABSENT), new RecordChange(b, AttendanceStatus.PRESENT)));

        assertEquals(AttendanceStatus.ABSENT, recordOf(session, studentA).getStatus());
        assertEquals(AttendanceStatus.PRESENT, recordOf(session, studentB).getStatus());
        assertSame(facultyC, recordOf(session, studentB).getMarkedByFacultyProfile());
        assertEquals(LATER, recordOf(session, studentB).getMarkedAt());
        assertEquals(AttendanceSessionStatus.FINALIZED, session.getStatus());
        assertEquals(NOW_STORED, session.getFinalizedAt());
    }

    @Test
    void markingAndFinalizingNotifyNobodyButARealCorrectionTellsThatStudent() {
        AttendanceSession session = createdSession();
        Long a = recordOf(session, studentA).getId();
        Long b = recordOf(session, studentB).getId();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(new RecordChange(a, AttendanceStatus.ABSENT)));
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(),
                List.of(new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(b, AttendanceStatus.ABSENT)));
        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());
        verifyNoInteractions(notifications);

        // Student A stays present, so only student B, whose record really changes, is told.
        serviceAt(LATER).saveRecords(FACULTY_C, session.getId(),
                List.of(new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(b, AttendanceStatus.PRESENT)));

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).notifyUser(message.capture(), eq(12L));
        verify(notifications, never()).notifyUser(any(), eq(11L));
        assertEquals(NotificationType.ATTENDANCE_UPDATED, message.getValue().type());
        assertEquals("/academic/attendance", message.getValue().actionPath());
        assertEquals(b, message.getValue().sourceId());
        assertEquals("Your attendance for a BCS301 Data Structures class was corrected to Present.", message.getValue().message());
    }

    @Test
    void finalizedAttendanceCannotGoBackToUnmarked() {
        AttendanceSession session = createdSession();
        Long a = recordOf(session, studentA).getId();
        Long b = recordOf(session, studentB).getId();
        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(),
                List.of(new RecordChange(a, AttendanceStatus.PRESENT), new RecordChange(b, AttendanceStatus.PRESENT)));
        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());

        assertRefused(HttpStatus.BAD_REQUEST, AttendanceManagementService.FINALIZED_UNMARK,
                () -> serviceAt(LATER).saveRecords(FACULTY_A, session.getId(),
                        List.of(new RecordChange(b, AttendanceStatus.ABSENT), new RecordChange(a, AttendanceStatus.UNMARKED))));

        assertEquals(AttendanceStatus.PRESENT, recordOf(session, studentA).getStatus());
        assertEquals(AttendanceStatus.PRESENT, recordOf(session, studentB).getStatus());
        assertEquals(NOW_STORED, recordOf(session, studentB).getMarkedAt());
    }

    @Test
    void onlyAnOpenSessionsDateAndTopicCanChange() {
        AttendanceSession session = createdSession();

        serviceAt(NOW).updateSession(FACULTY_A, session.getId(), LATER, "Unit 2 revision");
        assertEquals(LATER, session.getHeldAt());
        assertEquals("Unit 2 revision", session.getTopic());

        serviceAt(NOW).saveRecords(FACULTY_A, session.getId(), List.of(
                new RecordChange(recordOf(session, studentA).getId(), AttendanceStatus.PRESENT),
                new RecordChange(recordOf(session, studentB).getId(), AttendanceStatus.PRESENT)));
        serviceAt(NOW).finalizeSession(FACULTY_A, session.getId());

        assertRefused(HttpStatus.CONFLICT, AttendanceManagementService.FINALIZED_NOT_EDITABLE,
                () -> serviceAt(NOW).updateSession(FACULTY_A, session.getId(), HELD, "Changed"));
        assertEquals(LATER, session.getHeldAt());
    }
}
