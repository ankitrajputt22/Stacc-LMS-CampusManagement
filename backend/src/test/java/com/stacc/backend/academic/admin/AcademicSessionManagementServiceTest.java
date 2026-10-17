package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.AcademicSessionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.AcademicSessionResponse;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.academic.session.AcademicSessionStatus;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

class AcademicSessionManagementServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2027, 6, 30);

    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final AcademicSessionManagementService service = new AcademicSessionManagementService(sessions);
    private final AcademicSession session = MasterFixtures.session(2, "2026-27", 2026);

    @BeforeEach
    void setUp() {
        when(sessions.findById(2L)).thenReturn(Optional.of(session));
        when(sessions.saveAndFlush(any(AcademicSession.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 9L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    private AcademicSessionResponse update(AcademicSessionStatus status) {
        return service.update(2L, new AcademicSessionUpdate(START, END, status));
    }

    @Test
    void aNewSessionIsPlannedWithItsOwnCodeAndDates() {
        AcademicSessionResponse created = service.create(new AcademicSessionCreate(" 2027-28 ", LocalDate.of(2027, 7, 1),
                LocalDate.of(2028, 5, 31)));

        assertEquals(new AcademicSessionResponse(9L, "2027-28", LocalDate.of(2027, 7, 1), LocalDate.of(2028, 5, 31), "PLANNED"),
                created);
    }

    @Test
    void theEndMustBeAfterTheStart() {
        assertRefused(HttpStatus.BAD_REQUEST, AcademicSessionManagementService.DATES,
                () -> service.create(new AcademicSessionCreate("X", START, START)));
        assertRefused(HttpStatus.BAD_REQUEST, AcademicSessionManagementService.DATES,
                () -> service.update(2L, new AcademicSessionUpdate(END, START, AcademicSessionStatus.PLANNED)));
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void aTakenCodeAnswers409() {
        when(sessions.existsByCode("2026-27")).thenReturn(true);

        assertRefused(HttpStatus.CONFLICT, AcademicSessionManagementService.CODE_TAKEN,
                () -> service.create(new AcademicSessionCreate("2026-27", START, END)));
    }

    @Test
    void datesCanChangeAndTheStatusMovesForwardOnly() {
        AcademicSessionResponse moved = service.update(2L,
                new AcademicSessionUpdate(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31), AcademicSessionStatus.PLANNED));
        assertEquals(LocalDate.of(2026, 8, 1), moved.startDate());

        assertEquals("ACTIVE", update(AcademicSessionStatus.ACTIVE).status());
        assertRefused(HttpStatus.CONFLICT, AcademicSessionManagementService.BACK_TO_PLANNED, () -> update(AcademicSessionStatus.PLANNED));
        assertEquals("CLOSED", update(AcademicSessionStatus.CLOSED).status());
        assertRefused(HttpStatus.CONFLICT, AcademicSessionManagementService.REOPEN, () -> update(AcademicSessionStatus.ACTIVE));
        assertEquals(AcademicSessionStatus.CLOSED, session.getStatus());
    }

    @Test
    void anUnknownSessionAnswers404() {
        assertRefused(HttpStatus.NOT_FOUND, AcademicSessionManagementService.NOT_FOUND, () -> service.get(99L));
    }
}
