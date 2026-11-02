package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterRegistrationUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SemesterResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SemesterRegistrationPeriodTest {

    private static final Instant OPENS = Instant.parse("2026-07-01T03:30:00Z");
    private static final Instant CLOSES = Instant.parse("2026-07-15T11:30:00Z");
    private static final Instant DROP = Instant.parse("2026-07-31T11:30:00Z");

    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final SemesterManagementService service = new SemesterManagementService(
            semesters, mock(ProgramRepository.class), mock(AcademicSessionRepository.class));
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    private final AcademicSession session = MasterFixtures.session(2, "2026-27", 2026);
    private final Semester third = MasterFixtures.semester(5, btech, session, 3);

    @BeforeEach
    void setUp() {
        when(semesters.findWithContextById(5L)).thenReturn(Optional.of(third));
        when(semesters.saveAndFlush(any(Semester.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void assertRefused(HttpStatus status, String message, SemesterRegistrationUpdate request) {
        ApiException refused = assertThrows(ApiException.class, () -> service.updateRegistration(5L, request));
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
        verify(semesters, never()).saveAndFlush(any());
    }

    @Test
    void aPeriodAndDropDeadlineAreSavedAndReturned() {
        SemesterResponse saved = service.updateRegistration(5L, new SemesterRegistrationUpdate(OPENS, CLOSES, DROP));

        assertEquals(OPENS, saved.registrationOpensAt());
        assertEquals(CLOSES, saved.registrationClosesAt());
        assertEquals(DROP, saved.dropDeadline());
    }

    @Test
    void sendingNothingClearsThePeriod() {
        third.changeRegistrationPeriod(OPENS, CLOSES, DROP);

        SemesterResponse saved = service.updateRegistration(5L, new SemesterRegistrationUpdate(null, null, null));

        assertNull(saved.registrationOpensAt());
        assertNull(saved.registrationClosesAt());
        assertNull(saved.dropDeadline());
    }

    @Test
    void badPeriodsAreRefusedWithoutSaving() {
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.PERIOD_HALF,
                new SemesterRegistrationUpdate(OPENS, null, null));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.PERIOD_ORDER,
                new SemesterRegistrationUpdate(CLOSES, OPENS, null));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.PERIOD_ORDER,
                new SemesterRegistrationUpdate(OPENS, OPENS, null));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.DROP_WITHOUT_PERIOD,
                new SemesterRegistrationUpdate(null, null, DROP));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.DROP_BEFORE_OPEN,
                new SemesterRegistrationUpdate(OPENS, CLOSES, OPENS.minusSeconds(1)));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.YEAR_RANGE,
                new SemesterRegistrationUpdate(Instant.parse("1999-12-31T00:00:00Z"), CLOSES, null));
    }

    @Test
    void aClosedSemesterKeepsItsPeriod() {
        third.close();

        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.PERIOD_CLOSED,
                new SemesterRegistrationUpdate(OPENS, CLOSES, DROP));
    }

    @Test
    void registrationIsOpenFromTheOpeningMomentUntilJustBeforeClosing() {
        assertFalse(third.isRegistrationOpenAt(OPENS), "no period set");

        third.changeRegistrationPeriod(OPENS, CLOSES, DROP);

        assertFalse(third.isRegistrationOpenAt(OPENS.minusMillis(1)));
        assertTrue(third.isRegistrationOpenAt(OPENS));
        assertTrue(third.isRegistrationOpenAt(CLOSES.minusMillis(1)));
        assertFalse(third.isRegistrationOpenAt(CLOSES));
        assertTrue(third.isDropAllowedAt(DROP));
        assertFalse(third.isDropAllowedAt(DROP.plusMillis(1)));
    }

    @Test
    void aProgramCreditLimitIsPositiveAndAtMostSixty() {
        btech.changeMaxCreditsPerSemester(new BigDecimal("24.00"));
        assertEquals(new BigDecimal("24.00"), btech.getMaxCreditsPerSemester());

        btech.changeMaxCreditsPerSemester(null);
        assertNull(btech.getMaxCreditsPerSemester());

        assertThrows(IllegalArgumentException.class, () -> btech.changeMaxCreditsPerSemester(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> btech.changeMaxCreditsPerSemester(new BigDecimal("60.01")));
    }
}
