package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.DepartmentResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class DepartmentManagementServiceTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final DepartmentManagementService service = new DepartmentManagementService(departments);
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");

    @BeforeEach
    void setUp() {
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
        when(departments.saveAndFlush(any(Department.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 9L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aNewDepartmentIsActiveWithAnUppercaseCode() {
        DepartmentResponse created = service.create(new DepartmentCreate("  ece ", " Electronics and Communication "));

        assertEquals(new DepartmentResponse(9L, "ECE", "Electronics and Communication", "ACTIVE"), created);
    }

    @Test
    void aTakenCodeOrNameAnswers409AndNothingIsSaved() {
        when(departments.existsByCode("CSE")).thenReturn(true);
        when(departments.existsByName("Mathematics")).thenReturn(true);

        assertRefused(HttpStatus.CONFLICT, DepartmentManagementService.CODE_TAKEN,
                () -> service.create(new DepartmentCreate("cse", "Another name")));
        assertRefused(HttpStatus.CONFLICT, DepartmentManagementService.NAME_TAKEN,
                () -> service.create(new DepartmentCreate("MAT", "Mathematics")));
        verify(departments, never()).saveAndFlush(any());
    }

    @Test
    void aRaceThatTheDatabaseCatchesStillAnswersASafe409() {
        when(departments.saveAndFlush(any(Department.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'ECE' for key 'departments.uk_departments_code'"));

        assertRefused(HttpStatus.CONFLICT, DepartmentManagementService.TAKEN,
                () -> service.create(new DepartmentCreate("ECE", "Electronics")));
    }

    @Test
    void editingChangesTheNameAndStatusButNeverTheCode() {
        DepartmentResponse updated = service.update(3L, new DepartmentUpdate("Computing", DepartmentStatus.INACTIVE));

        assertEquals(new DepartmentResponse(3L, "CSE", "Computing", "INACTIVE"), updated);
        assertEquals("ACTIVE", service.update(3L, new DepartmentUpdate("Computing", DepartmentStatus.ACTIVE)).status());
    }

    @Test
    void anotherDepartmentsNameAnswers409() {
        when(departments.existsByNameAndIdNot("Mathematics", 3L)).thenReturn(true);

        assertRefused(HttpStatus.CONFLICT, DepartmentManagementService.NAME_TAKEN,
                () -> service.update(3L, new DepartmentUpdate("Mathematics", DepartmentStatus.ACTIVE)));
        assertEquals("Computer Science and Engineering", cse.getName());
    }

    @Test
    void anUnknownDepartmentAnswers404() {
        assertRefused(HttpStatus.NOT_FOUND, DepartmentManagementService.NOT_FOUND, () -> service.get(99L));
        assertRefused(HttpStatus.NOT_FOUND, DepartmentManagementService.NOT_FOUND,
                () -> service.update(99L, new DepartmentUpdate("X", DepartmentStatus.ACTIVE)));
    }

    @Test
    void theListIsInCodeOrderAsStored() {
        Department mat = MasterFixtures.department(4, "MAT", "Mathematics");
        when(departments.findAllByOrderByCodeAscIdAsc()).thenReturn(List.of(cse, mat));

        assertEquals(List.of("CSE", "MAT"), service.list().stream().map(DepartmentResponse::code).toList());
    }
}
