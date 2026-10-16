package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The single-course list trusts the faculty access service, which has its own tests. The list across courses
 * runs the real faculty course query over stand-in repositories, so it is shown to use exactly the courses
 * that rule allows. The assignment repository is a stand-in that may hand back too much.
 */
class LmsFacultyAssignmentQueryServiceTest {

    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;

    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsFacultyAssignmentQueryService service = new LmsFacultyAssignmentQueryService(
            facultyAccess, new LmsFacultyCourseQueryService(teachingAssignments, lmsCourses), assignments);

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Semester semester = new Semester(
            new Program(department, "BTECH", "Bachelor of Technology", 8),
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)),
            3);
    private final FacultyProfile faculty = faculty(ACCOUNT_ID);

    private final List<TeachingAssignment> taught = new ArrayList<>();
    private final List<LmsCourse> courses = new ArrayList<>();

    private FacultyProfile faculty(Long accountId) {
        UserAccount account = new UserAccount("TEST-FACULTY-" + accountId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new FacultyProfile(account, department);
    }

    /** A course with its own offering, and an active teaching assignment of this teacher to it. */
    private LmsCourse course(long id, String courseCode, FacultyProfile teacher) {
        Course catalogCourse = new Course(department, courseCode, "Course " + courseCode, new BigDecimal("4.00"));
        CourseOffering offering = new CourseOffering(catalogCourse, semester);
        ReflectionTestUtils.setField(offering, "id", id + 100);
        LmsCourse created = new LmsCourse(offering);
        ReflectionTestUtils.setField(created, "id", id);
        taught.add(new TeachingAssignment(teacher, offering));
        courses.add(created);
        return created;
    }

    private static LmsAssignment assignment(LmsCourse course, long id, LmsContentStatus status, String dueAt) {
        LmsAssignment created = new LmsAssignment(course, "Assignment " + id, "Instructions " + id,
                Instant.parse(dueAt), new BigDecimal("15"));
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "status", status);
        return created;
    }

    private void teachingAsListed() {
        when(teachingAssignments.findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID)).thenReturn(taught);
        when(lmsCourses.findAllByCourseOfferingIdIn(anyCollection())).thenReturn(courses);
    }

    @Test
    void courseListHasDraftsAndPublishedButNotArchivedAssignmentsEarliestDeadlineFirst() {
        LmsCourse course = course(7, "TST301", faculty);
        LmsCourse other = course(8, "TST302", faculty);
        when(facultyAccess.canAccessCourse(ACCOUNT_ID, 7L)).thenReturn(true);
        when(assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(7L, LmsContentPolicy.IN_USE)).thenReturn(List.of(
                assignment(course, 4, LmsContentStatus.PUBLISHED, "2026-11-01T18:29:00Z"),
                assignment(course, 3, LmsContentStatus.ARCHIVED, "2026-10-18T18:29:00Z"),
                assignment(other, 9, LmsContentStatus.DRAFT, "2026-10-19T18:29:00Z"),
                assignment(course, 2, LmsContentStatus.DRAFT, "2026-10-20T18:29:00Z"),
                assignment(course, 1, LmsContentStatus.PUBLISHED, "2020-01-10T18:29:00Z")));

        List<LmsFacultyAssignmentResponse> managed = service.getManagedAssignments(ACCOUNT_ID, 7L);

        assertEquals(List.of(1L, 2L, 4L), managed.stream().map(LmsFacultyAssignmentResponse::id).toList());
        assertEquals(LmsContentStatus.DRAFT, managed.get(1).status());
        assertEquals(new BigDecimal("15.00"), managed.get(1).maxMarks());
    }

    @Test
    void courseTheFacultyMemberMayNotUseIsUnavailableAndNothingIsRead() {
        ApiException refused = assertThrows(ApiException.class, () -> service.getManagedAssignments(OTHER_ACCOUNT_ID, 7L));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals("Course not found or unavailable.", refused.getMessage());
        verifyNoInteractions(assignments);
    }

    @Test
    void courseWithoutAssignmentsGivesAnEmptyList() {
        when(facultyAccess.canAccessCourse(ACCOUNT_ID, 7L)).thenReturn(true);

        assertEquals(List.of(), service.getManagedAssignments(ACCOUNT_ID, 7L));
    }

    @Test
    void overviewCoversOnlyTheCoursesTheFacultyMemberMayUseNow() {
        LmsCourse first = course(7, "TST301", faculty);
        LmsCourse second = course(8, "TST302", faculty);
        LmsCourse inactiveAssignment = course(9, "TST303", faculty);
        taught.get(2).deactivate();
        LmsCourse archivedCourse = course(10, "TST304", faculty);
        archivedCourse.archive();
        LmsCourse othersCourse = course(11, "TST305", faculty(OTHER_ACCOUNT_ID));
        teachingAsListed();
        when(assignments.findAllByLmsCourseIdInAndStatusInOrderByDueAtAscIdAsc(Set.of(7L, 8L), LmsContentPolicy.IN_USE))
                .thenReturn(List.of(
                        assignment(first, 1, LmsContentStatus.DRAFT, "2026-10-25T18:29:00Z"),
                        assignment(second, 2, LmsContentStatus.PUBLISHED, "2026-10-20T18:29:00Z"),
                        assignment(first, 3, LmsContentStatus.ARCHIVED, "2026-10-19T18:29:00Z"),
                        assignment(first, 4, LmsContentStatus.PUBLISHED, "2020-02-01T18:29:00Z"),
                        // A query that returned too much still shows nothing of these courses.
                        assignment(inactiveAssignment, 5, LmsContentStatus.PUBLISHED, "2026-10-21T18:29:00Z"),
                        assignment(archivedCourse, 6, LmsContentStatus.PUBLISHED, "2026-10-21T18:29:00Z"),
                        assignment(othersCourse, 7, LmsContentStatus.PUBLISHED, "2026-10-21T18:29:00Z")));

        List<LmsFacultyAssignmentOverviewResponse> overview = service.getMyAssignments(ACCOUNT_ID);

        assertEquals(List.of(4L, 2L, 1L), overview.stream().map(LmsFacultyAssignmentOverviewResponse::assignmentId).toList());
        assertEquals(new LmsFacultyAssignmentOverviewResponse(8L, "TST302", "Course TST302", 2L, "Assignment 2",
                Instant.parse("2026-10-20T18:29:00Z"), new BigDecimal("15.00"), LmsContentStatus.PUBLISHED), overview.get(1));
        verify(assignments).findAllByLmsCourseIdInAndStatusInOrderByDueAtAscIdAsc(Set.of(7L, 8L), LmsContentPolicy.IN_USE);
    }

    @Test
    void inactiveFacultyProfileGetsAnEmptyOverviewWithoutReadingAssignments() {
        course(7, "TST301", faculty);
        faculty.deactivate();
        teachingAsListed();

        assertEquals(List.of(), service.getMyAssignments(ACCOUNT_ID));
        verifyNoInteractions(assignments);
    }

    @Test
    void facultyMemberWithoutCoursesGetsAnEmptyOverview() {
        teachingAsListed();

        assertEquals(List.of(), service.getMyAssignments(ACCOUNT_ID));
        assertEquals(List.of(), service.getMyAssignments(null));
        verifyNoInteractions(assignments);
    }
}
