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

import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SectionResponse;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.section.SectionStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

class SectionManagementServiceTest {

    private final SectionRepository sections = mock(SectionRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final SemesterEnrollmentRepository enrollments = mock(SemesterEnrollmentRepository.class);
    private final SectionManagementService service = new SectionManagementService(sections, semesters, enrollments);
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    private final Semester third = MasterFixtures.semester(9, btech, MasterFixtures.session(2, "2026-27", 2026), 3);

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aSectionIsCreatedInUppercaseInAnOpenSemester() {
        when(semesters.findWithContextById(9L)).thenReturn(Optional.of(third));
        when(sections.saveAndFlush(any(Section.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 21L));

        SectionResponse created = service.create(new SectionCreate(9L, " a ", "Section A", 60));

        assertEquals(new SectionResponse(21L, 9L, 3, 4L, "BTECH-CSE", 2L, "2026-27", "A", "Section A", 60, 0, "ACTIVE"), created);
    }

    @Test
    void aClosedSemesterOrATakenCodeIsRefused() {
        when(semesters.findWithContextById(9L)).thenReturn(Optional.of(third));
        when(sections.existsBySemesterIdAndCode(9L, "A")).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, SectionManagementService.CODE_TAKEN,
                () -> service.create(new SectionCreate(9L, "a", null, null)));

        third.close();
        assertRefused(HttpStatus.CONFLICT, SectionManagementService.SEMESTER_CLOSED,
                () -> service.create(new SectionCreate(9L, "B", null, null)));
        assertRefused(HttpStatus.BAD_REQUEST, SectionManagementService.SEMESTER_MISSING,
                () -> service.create(new SectionCreate(99L, "B", null, null)));
        verify(sections, never()).saveAndFlush(any());
    }

    @Test
    void theCapacityNeverDropsBelowTheStudentsEnrolled() {
        Section a = MasterFixtures.withId(new Section(third, "A", null, 60), 21L);
        when(sections.findByIdForUpdate(21L)).thenReturn(Optional.of(a));
        when(sections.findWithContextById(21L)).thenReturn(Optional.of(a));
        when(enrollments.countEnrolledInSectionForShare(21L)).thenReturn(40L);

        assertRefused(HttpStatus.CONFLICT, "This section already has 40 enrolled students, so its capacity cannot be lower than that.",
                () -> service.update(21L, new SectionUpdate("A", 39, SectionStatus.ACTIVE)));

        SectionResponse changed = service.update(21L, new SectionUpdate("Morning", 40, SectionStatus.INACTIVE));
        assertEquals(40, changed.capacity());
        assertEquals("INACTIVE", changed.status());
        assertEquals(40, changed.enrolledCount());
    }

    @Test
    void theListCarriesEachSectionsEnrolledCount() {
        Section a = MasterFixtures.withId(new Section(third, "A", null, null), 21L);
        Section b = MasterFixtures.withId(new Section(third, "B", null, null), 22L);
        when(sections.findAllWithContextBySemesterId(9L)).thenReturn(List.of(a, b));
        when(enrollments.countEnrolledBySection()).thenReturn(List.<Object[]>of(new Object[] {21L, 3L}));

        List<SectionResponse> listed = service.list(9L);

        assertEquals(3, listed.get(0).enrolledCount());
        assertEquals(0, listed.get(1).enrolledCount());
    }
}
