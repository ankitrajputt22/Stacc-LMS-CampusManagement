package com.stacc.backend.academic.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AcademicSessionTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private static final LocalDate END = LocalDate.of(2027, 6, 30);

    @Test
    void newSessionIsPlanned() {
        AcademicSession session = new AcademicSession("2026-27", START, END);

        assertEquals("2026-27", session.getCode());
        assertEquals(START, session.getStartDate());
        assertEquals(END, session.getEndDate());
        assertEquals(AcademicSessionStatus.PLANNED, session.getStatus());
    }

    @Test
    void codeIsTrimmedAndOtherwiseKeptAsGiven() {
        assertEquals("2026-27", new AcademicSession("  2026-27 ", START, END).getCode());
        assertEquals("AY2026", new AcademicSession("ay2026", START, END).getCode());
    }

    @Test
    void codeIsRequiredAndLimitedInLength() {
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession(null, START, END));
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession("  ", START, END));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AcademicSession("2".repeat(AcademicSession.MAX_CODE_LENGTH + 1), START, END));
    }

    @Test
    void bothDatesAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession("2026-27", null, END));
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession("2026-27", START, null));
    }

    @Test
    void sessionMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession("2026-27", START, START));
        assertThrows(IllegalArgumentException.class, () -> new AcademicSession("2026-27", END, START));
    }

    @Test
    void sessionDoesNotHaveToBeExactlyOneYearLong() {
        AcademicSession shortSession = new AcademicSession("2026-S", START, START.plusDays(1));
        AcademicSession longSession = new AcademicSession("2026-L", START, START.plusMonths(18));

        assertEquals(START.plusDays(1), shortSession.getEndDate());
        assertEquals(START.plusMonths(18), longSession.getEndDate());
    }

    @Test
    void reschedulingKeepsTheSameDateRule() {
        AcademicSession session = new AcademicSession("2026-27", START, END);

        session.reschedule(START.plusDays(14), END.plusDays(14));

        assertEquals(START.plusDays(14), session.getStartDate());
        assertEquals(END.plusDays(14), session.getEndDate());
        assertThrows(IllegalArgumentException.class, () -> session.reschedule(END, START));
        assertThrows(IllegalArgumentException.class, () -> session.reschedule(null, END));
        assertEquals(START.plusDays(14), session.getStartDate());
    }

    @Test
    void plannedSessionBecomesActiveAndThenClosed() {
        AcademicSession session = new AcademicSession("2026-27", START, END);

        session.markActive();
        assertEquals(AcademicSessionStatus.ACTIVE, session.getStatus());

        session.close();
        assertEquals(AcademicSessionStatus.CLOSED, session.getStatus());
    }

    @Test
    void closedSessionCannotBeMadeActiveAgain() {
        AcademicSession session = new AcademicSession("2026-27", START, END);
        session.close();

        assertThrows(IllegalStateException.class, session::markActive);
        assertEquals(AcademicSessionStatus.CLOSED, session.getStatus());
    }
}
