package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SemesterResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

class SemesterManagementServiceTest {

    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final SemesterManagementService service = new SemesterManagementService(semesters, programs, sessions);
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    private final AcademicSession session = MasterFixtures.session(2, "2026-27", 2026);
    private final Semester third = MasterFixtures.semester(5, btech, session, 3);

    @BeforeEach
    void setUp() {
        when(programs.findWithDepartmentById(4L)).thenReturn(Optional.of(btech));
        when(sessions.findById(2L)).thenReturn(Optional.of(session));
        when(semesters.findWithContextById(5L)).thenReturn(Optional.of(third));
        when(semesters.saveAndFlush(any(Semester.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 9L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aSemesterJoinsAnExistingProgramAndSessionLoadedOnTheServer() {
        SemesterResponse created = service.create(new SemesterCreate(4L, 2L, 5));

        assertEquals(new SemesterResponse(9L, 4L, "BTECH-CSE", "Program BTECH-CSE", "CSE", 2L, "2026-27", 5, "PLANNED"), created);
    }

    @Test
    void aMissingProgramOrSessionIsRefusedAndNothingIsCreated() {
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.PROGRAM_MISSING, () -> service.create(new SemesterCreate(99L, 2L, 1)));
        assertRefused(HttpStatus.BAD_REQUEST, SemesterManagementService.SESSION_MISSING, () -> service.create(new SemesterCreate(4L, 99L, 1)));
        verify(semesters, never()).saveAndFlush(any());
    }

    @Test
    void theNumberCannotExceedTheProgramsDurationOrRepeat() {
        assertRefused(HttpStatus.BAD_REQUEST, "Semester number must not be greater than the program's 8 semesters.",
                () -> service.create(new SemesterCreate(4L, 2L, 9)));
        when(semesters.findByProgramIdAndAcademicSessionIdAndSemesterNumber(4L, 2L, 3)).thenReturn(Optional.of(third));
        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.TAKEN, () -> service.create(new SemesterCreate(4L, 2L, 3)));
    }

    @Test
    void onlyTheStatusChangesAndItMovesForwardOnly() {
        assertEquals("ACTIVE", service.update(5L, new SemesterUpdate(SemesterStatus.ACTIVE)).status());
        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.BACK_TO_PLANNED,
                () -> service.update(5L, new SemesterUpdate(SemesterStatus.PLANNED)));
        SemesterResponse closed = service.update(5L, new SemesterUpdate(SemesterStatus.CLOSED));
        assertEquals("CLOSED", closed.status());
        assertEquals(3, closed.semesterNumber());
        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.REOPEN, () -> service.update(5L, new SemesterUpdate(SemesterStatus.ACTIVE)));
    }

    @Test
    void anUnknownSemesterAnswers404() {
        assertRefused(HttpStatus.NOT_FOUND, SemesterManagementService.NOT_FOUND, () -> service.get(99L));
    }
}
