package com.stacc.backend.academic.semester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SemesterTest {

    private final Program program = new Program(
            new Department("CSE", "Computer Science and Engineering"), "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));

    @Test
    void newSemesterIsPlannedAndLinksItsProgramAndSession() {
        Semester semester = new Semester(program, session, 3);

        assertSame(program, semester.getProgram());
        assertSame(session, semester.getAcademicSession());
        assertEquals(3, semester.getSemesterNumber());
        assertEquals(SemesterStatus.PLANNED, semester.getStatus());
    }

    @Test
    void programAndSessionAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Semester(null, session, 1));
        assertThrows(IllegalArgumentException.class, () -> new Semester(program, null, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21, 100})
    void semesterNumberMustBePositiveAndWithinTheOverallLimit(int number) {
        assertThrows(IllegalArgumentException.class, () -> new Semester(program, session, number));
    }

    @Test
    void semesterNumberMustFitWithinTheProgramsLength() {
        assertEquals(1, new Semester(program, session, 1).getSemesterNumber());
        assertEquals(8, new Semester(program, session, 8).getSemesterNumber());
        assertThrows(IllegalArgumentException.class, () -> new Semester(program, session, 9));
    }

    @Test
    void oddAndEvenSemestersCanShareOneSession() {
        assertEquals(3, new Semester(program, session, 3).getSemesterNumber());
        assertEquals(4, new Semester(program, session, 4).getSemesterNumber());
    }

    @Test
    void plannedSemesterBecomesActiveAndThenClosed() {
        Semester semester = new Semester(program, session, 3);

        semester.markActive();
        assertEquals(SemesterStatus.ACTIVE, semester.getStatus());

        semester.close();
        assertEquals(SemesterStatus.CLOSED, semester.getStatus());
    }

    @Test
    void closedSemesterCannotBeMadeActiveAgain() {
        Semester semester = new Semester(program, session, 3);
        semester.close();

        assertThrows(IllegalStateException.class, semester::markActive);
        assertEquals(SemesterStatus.CLOSED, semester.getStatus());
    }
}
