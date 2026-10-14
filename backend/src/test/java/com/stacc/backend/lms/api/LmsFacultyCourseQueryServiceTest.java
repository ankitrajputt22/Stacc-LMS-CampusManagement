package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LmsFacultyCourseQueryServiceTest {

    // Made-up IDs used only by these tests.
    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsFacultyCourseQueryService service = new LmsFacultyCourseQueryService(teachingAssignments, lmsCourses);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final FacultyProfile faculty = faculty(ACCOUNT_ID);

    private final List<TeachingAssignment> assignments = new ArrayList<>();
    private final List<LmsCourse> courses = new ArrayList<>();
    private long nextOfferingId = 50;

    private FacultyProfile faculty(Long accountId) {
        UserAccount account = new UserAccount("TEST-FACULTY-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new FacultyProfile(account, department);
    }

    private CourseOffering offering(String courseCode, Semester offeredIn) {
        Course course = new Course(department, courseCode, "Test Course " + courseCode, new BigDecimal("4.00"));
        CourseOffering created = new CourseOffering(course, offeredIn);
        ReflectionTestUtils.setField(created, "id", nextOfferingId++);
        return created;
    }

    private static LmsCourse lmsCourse(CourseOffering courseOffering, long id) {
        LmsCourse created = new LmsCourse(courseOffering);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    /** An active assignment of this faculty member to a new offering, with a draft LMS course. */
    private LmsCourse taught(String courseCode, long lmsCourseId) {
        return taught(faculty, offering(courseCode, semester), lmsCourseId);
    }

    private LmsCourse taught(FacultyProfile teacher, CourseOffering offering, long lmsCourseId) {
        assignments.add(new TeachingAssignment(teacher, offering));
        LmsCourse created = lmsCourse(offering, lmsCourseId);
        courses.add(created);
        return created;
    }

    private List<LmsFacultyCourseSummaryResponse> list() {
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID)).thenReturn(assignments);
        when(lmsCourses.findAllByCourseOfferingIdIn(anyCollection())).thenReturn(courses);
        return service.getAccessibleCourses(ACCOUNT_ID);
    }

    private static List<Long> idsOf(List<LmsFacultyCourseSummaryResponse> listed) {
        return listed.stream().map(LmsFacultyCourseSummaryResponse::lmsCourseId).toList();
    }

    @Test
    void draftAndPublishedCoursesAreListedWithTheirStatusAndAcademicDetails() {
        taught("TST302", 8);
        taught("TST301", 7).publish();

        List<LmsFacultyCourseSummaryResponse> listed = list();

        assertEquals(List.of(
                new LmsFacultyCourseSummaryResponse(7L, "TST301", "Test Course TST301", new BigDecimal("4.00"), 3, "BTECH", "2026-27", "PUBLISHED"),
                new LmsFacultyCourseSummaryResponse(8L, "TST302", "Test Course TST302", new BigDecimal("4.00"), 3, "BTECH", "2026-27", "DRAFT")),
                listed);
    }

    @Test
    void archivedLmsCourseIsLeftOut() {
        taught("TST301", 7);
        taught("TST302", 8).archive();

        assertEquals(List.of(7L), idsOf(list()));
    }

    @Test
    void inactiveTeachingAssignmentIsLeftOut() {
        taught("TST301", 7);
        taught("TST302", 8);
        assignments.get(1).deactivate();

        assertEquals(List.of(7L), idsOf(list()));
    }

    @Test
    void inactiveFacultyProfileListsNothing() {
        taught("TST301", 7);
        taught("TST302", 8);
        faculty.deactivate();

        assertTrue(list().isEmpty());
    }

    @Test
    void anotherFacultyMembersAssignmentIsLeftOut() {
        taught("TST301", 7);
        // Even if the lookup returned someone else's assignment, it would not be listed for this account.
        taught(faculty(OTHER_ACCOUNT_ID), offering("TST302", semester), 8);

        assertEquals(List.of(7L), idsOf(list()));
    }

    @Test
    void assignmentWhoseOfferingHasNoLmsCourseIsLeftOutAndNothingIsCreated() {
        taught("TST301", 7);
        assignments.add(new TeachingAssignment(faculty, offering("TST302", semester)));

        assertEquals(List.of(7L), idsOf(list()));
        verify(teachingAssignments).findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID);
        verify(lmsCourses).findAllByCourseOfferingIdIn(anyCollection());
        verifyNoMoreInteractions(teachingAssignments, lmsCourses);
    }

    @Test
    void lmsCoursesAreLookedUpForExactlyTheAssignedOfferings() {
        LmsCourse first = taught("TST301", 7);
        LmsCourse second = taught("TST302", 8);

        list();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> asked = ArgumentCaptor.forClass(Collection.class);
        verify(lmsCourses).findAllByCourseOfferingIdIn(asked.capture());
        assertEquals(
                Set.of(first.getCourseOffering().getId(), second.getCourseOffering().getId()),
                Set.copyOf(asked.getValue()));
    }

    @Test
    void coursesAreOrderedByCourseCodeThenByLmsCourseId() {
        Semester laterSemester = new Semester(program,
                new AcademicSession("2027-28", LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30)), 5);
        taught("TST302", 4);
        taught(faculty, offering("TST301", laterSemester), 9);
        taught("TST301", 6);

        assertEquals(List.of(6L, 9L, 4L), idsOf(list()));
    }

    @Test
    void facultyMemberWithNoAssignmentsGetsAnEmptyListWithoutAnLmsLookup() {
        assertTrue(list().isEmpty());

        verifyNoInteractions(lmsCourses);
    }

    @Test
    void missingAccountIdGivesAnEmptyListWithoutAnyLookup() {
        assertTrue(service.getAccessibleCourses(null).isEmpty());

        verifyNoInteractions(teachingAssignments, lmsCourses);
    }
}
