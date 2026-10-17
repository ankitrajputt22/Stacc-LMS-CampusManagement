package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.ProgramResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.program.ProgramStatus;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

class ProgramManagementServiceTest {

    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final ProgramManagementService service = new ProgramManagementService(programs, departments, semesters);
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);

    @BeforeEach
    void setUp() {
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
        when(programs.findWithDepartmentById(4L)).thenReturn(Optional.of(btech));
        when(semesters.findMaxSemesterNumberByProgramId(anyLong())).thenReturn(Optional.empty());
        when(programs.saveAndFlush(any(Program.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 9L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aProgramIsCreatedInTheExistingDepartmentLoadedOnTheServer() {
        ProgramResponse created = service.create(new ProgramCreate(3L, "mtech-cse", "Master of Technology", 4));

        assertEquals(new ProgramResponse(9L, 3L, "CSE", "Computer Science and Engineering", "MTECH-CSE", "Master of Technology",
                4, "ACTIVE"), created);
    }

    @Test
    void aMissingDepartmentIsRefusedAndNothingIsCreated() {
        assertRefused(HttpStatus.BAD_REQUEST, ProgramManagementService.DEPARTMENT_MISSING,
                () -> service.create(new ProgramCreate(99L, "X", "X", 4)));
        verify(programs, never()).saveAndFlush(any());
        verify(departments, never()).saveAndFlush(any());
    }

    @Test
    void aCodeOrNameTakenInTheSameDepartmentAnswers409() {
        when(programs.existsByDepartmentIdAndCode(3L, "BTECH-CSE")).thenReturn(true);
        when(programs.existsByDepartmentIdAndName(3L, "Program BTECH-CSE")).thenReturn(true);

        assertRefused(HttpStatus.CONFLICT, ProgramManagementService.CODE_TAKEN,
                () -> service.create(new ProgramCreate(3L, "btech-cse", "New name", 8)));
        assertRefused(HttpStatus.CONFLICT, ProgramManagementService.NAME_TAKEN,
                () -> service.create(new ProgramCreate(3L, "NEW", "Program BTECH-CSE", 8)));
    }

    @Test
    void editingChangesNameDurationAndStatusButNeverTheDepartmentOrCode() {
        ProgramResponse updated = service.update(4L, new ProgramUpdate("B.Tech. CSE", 10, ProgramStatus.INACTIVE));

        assertEquals(new ProgramResponse(4L, 3L, "CSE", "Computer Science and Engineering", "BTECH-CSE", "B.Tech. CSE", 10,
                "INACTIVE"), updated);
    }

    @Test
    void theDurationCannotDropBelowAnExistingSemester() {
        when(semesters.findMaxSemesterNumberByProgramId(4L)).thenReturn(Optional.of(6));

        assertRefused(HttpStatus.CONFLICT, "This program already has semester 6, so its duration cannot be shorter than 6 semesters.",
                () -> service.update(4L, new ProgramUpdate("B.Tech. CSE", 5, ProgramStatus.ACTIVE)));
        assertEquals(8, btech.getDurationSemesters());
        assertEquals(6, service.update(4L, new ProgramUpdate("B.Tech. CSE", 6, ProgramStatus.ACTIVE)).durationSemesters());
    }

    @Test
    void anUnknownProgramAnswers404() {
        assertRefused(HttpStatus.NOT_FOUND, ProgramManagementService.NOT_FOUND, () -> service.get(99L));
    }
}
