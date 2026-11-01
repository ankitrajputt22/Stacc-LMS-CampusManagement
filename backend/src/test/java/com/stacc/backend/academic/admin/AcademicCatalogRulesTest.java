package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseUpdate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.DepartmentCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.ProgramCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SemesterCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.DegreeType;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

/**
 * The v0.3 catalog details and rules: descriptions, degree and course types, inactive or closed parents taking no new
 * children, and one current academic session.
 */
class AcademicCatalogRulesTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);

    private final Department cse = MasterFixtures.department(3, "CSE", "Computer Science and Engineering");
    private final Program btech = MasterFixtures.program(4, cse, "BTECH-CSE", 8);
    private final AcademicSession current = MasterFixtures.session(2, "2026-27", 2026);
    private final AcademicSession next = MasterFixtures.session(5, "2027-28", 2027);

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aDepartmentKeepsItsDescriptionAndBlankClearsIt() {
        when(departments.saveAndFlush(any(Department.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 7L));
        DepartmentManagementService service = new DepartmentManagementService(departments);

        assertEquals("Computing.", service.create(new DepartmentCreate("me", "Mechanical", "  Computing.  ")).description());
        assertNull(service.create(new DepartmentCreate("ee", "Electrical", "   ")).description());
    }

    @Test
    void anInactiveDepartmentTakesNoNewProgramsOrCourses() {
        cse.deactivate();
        when(departments.findById(3L)).thenReturn(Optional.of(cse));

        assertRefused(HttpStatus.CONFLICT, ProgramManagementService.DEPARTMENT_INACTIVE,
                () -> new ProgramManagementService(programs, departments, semesters)
                        .create(new ProgramCreate(3L, "MTECH", "M.Tech", 4, DegreeType.POSTGRADUATE)));
        assertRefused(HttpStatus.CONFLICT, CourseManagementService.DEPARTMENT_INACTIVE,
                () -> new CourseManagementService(courses, departments)
                        .create(new CourseCreate(3L, "CS999", "New", new BigDecimal("3"), null, null, null)));
        verify(programs, never()).saveAndFlush(any());
        verify(courses, never()).saveAndFlush(any());
    }

    @Test
    void aProgramIsUndergraduateUnlessToldOtherwise() {
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
        when(programs.saveAndFlush(any(Program.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 9L));
        ProgramManagementService service = new ProgramManagementService(programs, departments, semesters);

        assertEquals("UNDERGRADUATE", service.create(new ProgramCreate(3L, "BCA", "BCA", 6, null)).degreeType());
        assertEquals("POSTGRADUATE", service.create(new ProgramCreate(3L, "MCA", "MCA", 4, DegreeType.POSTGRADUATE)).degreeType());
    }

    @Test
    void aCourseKeepsItsShortNameDescriptionAndTypeAndAnUpdateWithoutATypeKeepsIt() {
        Course dbms = MasterFixtures.course(13, cse, "BCS501", "4");
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
        when(courses.findWithDepartmentById(13L)).thenReturn(Optional.of(dbms));
        when(courses.saveAndFlush(any(Course.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 13L));
        CourseManagementService service = new CourseManagementService(courses, departments);

        var created = service.create(new CourseCreate(3L, "bcs502", "DBMS Lab", new BigDecimal("2"), "DBMS-L", "Practicals.",
                CourseType.LAB));
        assertEquals("DBMS-L", created.shortName());
        assertEquals("Practicals.", created.description());
        assertEquals("LAB", created.courseType());

        dbms.describe("DBMS", null, CourseType.ELECTIVE);
        var updated = service.update(13L, new CourseUpdate("Databases", new BigDecimal("4"), CourseStatus.ACTIVE, "", null, null));
        assertNull(updated.shortName());
        assertEquals("ELECTIVE", updated.courseType());
    }

    @Test
    void anInactiveProgramOrClosedSessionTakesNoNewSemesters() {
        when(programs.findWithDepartmentById(4L)).thenReturn(Optional.of(btech));
        when(sessions.findById(2L)).thenReturn(Optional.of(current));
        SemesterManagementService service = new SemesterManagementService(semesters, programs, sessions);

        btech.deactivate();
        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.PROGRAM_INACTIVE,
                () -> service.create(new SemesterCreate(4L, 2L, 5)));
        btech.activate();
        current.close();
        assertRefused(HttpStatus.CONFLICT, SemesterManagementService.SESSION_CLOSED,
                () -> service.create(new SemesterCreate(4L, 2L, 5)));
        verify(semesters, never()).saveAndFlush(any());
    }

    @Test
    void anInactiveCourseOrClosedSemesterTakesNoNewOfferings() {
        Course dbms = MasterFixtures.course(13, cse, "BCS501", "4");
        Semester fifth = MasterFixtures.semester(10, btech, current, 5);
        when(courses.findById(13L)).thenReturn(Optional.of(dbms));
        when(semesters.findWithContextById(10L)).thenReturn(Optional.of(fifth));
        CourseOfferingManagementService service = new CourseOfferingManagementService(offerings, courses, semesters, mock(SectionRepository.class), mock(OfferingFacultyLoader.class));

        dbms.deactivate();
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.COURSE_INACTIVE,
                () -> service.create(new CourseOfferingCreate(13L, 10L, null, null, null)));
        dbms.activate();
        fifth.close();
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.SEMESTER_CLOSED,
                () -> service.create(new CourseOfferingCreate(13L, 10L, null, null, null)));
        verify(offerings, never()).saveAndFlush(any(CourseOffering.class));
    }

    @Test
    void makingASessionCurrentMovesTheMarkFromThePreviousOne() {
        current.markActive();
        current.makeCurrent();
        when(sessions.findById(5L)).thenReturn(Optional.of(next));
        when(sessions.findFirstByCurrentTrue()).thenReturn(Optional.of(current));
        when(sessions.saveAndFlush(any(AcademicSession.class))).thenAnswer(call -> call.getArgument(0));
        AcademicSessionManagementService service = new AcademicSessionManagementService(sessions);

        assertTrue(service.makeCurrent(5L).current());
        assertFalse(current.isCurrent());
        assertTrue(next.isCurrent());
    }

    @Test
    void theCurrentSessionAgainChangesNothingAndAClosedOneCannotBeCurrent() {
        current.markActive();
        current.makeCurrent();
        when(sessions.findById(2L)).thenReturn(Optional.of(current));
        when(sessions.findById(5L)).thenReturn(Optional.of(next));
        when(sessions.findFirstByCurrentTrue()).thenReturn(Optional.of(current));
        AcademicSessionManagementService service = new AcademicSessionManagementService(sessions);

        assertTrue(service.makeCurrent(2L).current());
        verify(sessions, never()).saveAndFlush(any());

        next.close();
        assertRefused(HttpStatus.CONFLICT, AcademicSessionManagementService.CLOSED_CANNOT_BE_CURRENT,
                () -> service.makeCurrent(5L));
    }

    @Test
    void closingTheCurrentSessionAlsoStopsItBeingCurrent() {
        current.markActive();
        current.makeCurrent();

        current.close();

        assertFalse(current.isCurrent());
        assertThrows(IllegalStateException.class, current::makeCurrent);
        verify(sessions, never()).findById(anyLong());
    }
}
