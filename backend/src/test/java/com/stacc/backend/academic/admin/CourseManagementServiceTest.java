package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.CourseResponse;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** A course needs only its owning department: no offering, semester, teacher, or LMS course. */
class CourseManagementServiceTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final CourseManagementService service = new CourseManagementService(courses, departments);
    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Course structures = MasterFixtures.course(12, cse, "CS301", "4");

    @BeforeEach
    void setUp() {
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
        when(courses.findWithDepartmentById(12L)).thenReturn(Optional.of(structures));
        when(courses.saveAndFlush(any(Course.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 20L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aCourseIsCreatedWithItsDepartmentAndExactCredits() {
        CourseResponse created = service.create(new CourseCreate(3L, " cs105 ", " Lab Practice ", new BigDecimal("1.5")));

        assertEquals(new CourseResponse(20L, 3L, "CSE", "Computer Science and Engineering", "CS105", "Lab Practice",
                new BigDecimal("1.50"), "ACTIVE"), created);
    }

    @Test
    void aMissingDepartmentIsRefusedAndNothingIsCreated() {
        assertRefused(HttpStatus.BAD_REQUEST, CourseManagementService.DEPARTMENT_MISSING,
                () -> service.create(new CourseCreate(99L, "CS999", "X", BigDecimal.ONE)));
        verify(courses, never()).saveAndFlush(any());
    }

    @Test
    void aTakenCodeAnswers409EvenWhenTheDatabaseCatchesIt() {
        when(courses.existsByCode("CS301")).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, CourseManagementService.CODE_TAKEN,
                () -> service.create(new CourseCreate(3L, "cs301", "Again", BigDecimal.ONE)));

        when(courses.saveAndFlush(any(Course.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));
        assertRefused(HttpStatus.CONFLICT, CourseManagementService.CODE_TAKEN,
                () -> service.create(new CourseCreate(3L, "CS302", "Race", BigDecimal.ONE)));
    }

    @Test
    void editingChangesNameCreditsAndStatusButNeverTheDepartmentOrCode() {
        CourseResponse updated = service.update(12L, new CourseUpdate("Data Structures II", new BigDecimal("3.75"), CourseStatus.INACTIVE));

        assertEquals(new CourseResponse(12L, 3L, "CSE", "Computer Science and Engineering", "CS301", "Data Structures II",
                new BigDecimal("3.75"), "INACTIVE"), updated);
    }

    @Test
    void anUnknownCourseAnswers404() {
        assertRefused(HttpStatus.NOT_FOUND, CourseManagementService.NOT_FOUND, () -> service.get(99L));
        assertRefused(HttpStatus.NOT_FOUND, CourseManagementService.NOT_FOUND,
                () -> service.update(99L, new CourseUpdate("X", BigDecimal.ONE, CourseStatus.ACTIVE)));
    }
}
