package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The repository is a stand-in, so it can hand back assignments in any state, course, or order. That shows
 * the student rule holds on its own, even if a query ever returned too much. Deadlines are fixed moments;
 * one is long past, and the service never looks at the clock.
 */
class LmsStudentAssignmentQueryServiceTest {

    private static final Long STUDENT_ID = 12L;
    private static final Long OTHER_STUDENT_ID = 13L;
    private static final Long COURSE_ID = 7L;

    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsStudentAssignmentQueryService service = new LmsStudentAssignmentQueryService(studentAccess, assignments);

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private final LmsCourse otherCourse = ContentFixtures.lmsCourse(8);

    private static LmsAssignment assignment(LmsCourse course, long id, LmsContentStatus status, String dueAt) {
        LmsAssignment created = new LmsAssignment(course, "Assignment " + id, "Instructions " + id,
                Instant.parse(dueAt), new BigDecimal("20"));
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "status", status);
        if (status != LmsContentStatus.DRAFT) {
            ReflectionTestUtils.setField(created, "publishedAt", Instant.parse("2026-10-01T09:00:00Z"));
        }
        return created;
    }

    private void listed(LmsAssignment... found) {
        when(assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY))
                .thenReturn(List.of(found));
    }

    private void found(LmsAssignment assignment) {
        when(assignments.findWithCourseByIdAndLmsCourseId(assignment.getId(), COURSE_ID)).thenReturn(Optional.of(assignment));
    }

    private static void assertUnavailable(String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @BeforeEach
    void studentWhoMayUseTheCourse() {
        when(studentAccess.canAccessCourse(STUDENT_ID, COURSE_ID)).thenReturn(true);
    }

    @Test
    void onlyPublishedAssignmentsOfTheCourseAreListedEarliestDeadlineFirst() {
        listed(
                assignment(course, 4, LmsContentStatus.PUBLISHED, "2026-11-05T18:29:00Z"),
                assignment(course, 2, LmsContentStatus.DRAFT, "2026-10-20T18:29:00Z"),
                assignment(course, 3, LmsContentStatus.ARCHIVED, "2026-10-21T18:29:00Z"),
                assignment(otherCourse, 9, LmsContentStatus.PUBLISHED, "2026-10-22T18:29:00Z"),
                assignment(course, 1, LmsContentStatus.PUBLISHED, "2026-10-25T18:29:00Z"),
                assignment(course, 5, LmsContentStatus.PUBLISHED, "2026-10-25T18:29:00Z"));

        List<LmsCourseAssignmentResponse> listed = service.getPublishedAssignments(STUDENT_ID, COURSE_ID);

        assertEquals(List.of(1L, 5L, 4L), listed.stream().map(LmsCourseAssignmentResponse::id).toList());
        assertEquals(new LmsCourseAssignmentResponse(1L, "Assignment 1", Instant.parse("2026-10-25T18:29:00Z"),
                new BigDecimal("20.00")), listed.get(0));
        verify(assignments).findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(COURSE_ID, LmsContentPolicy.PUBLISHED_ONLY);
    }

    @Test
    void pastDuePublishedAssignmentIsStillListed() {
        listed(assignment(course, 1, LmsContentStatus.PUBLISHED, "2020-03-01T10:00:00Z"));

        assertEquals(List.of(1L), service.getPublishedAssignments(STUDENT_ID, COURSE_ID).stream()
                .map(LmsCourseAssignmentResponse::id).toList());
    }

    @Test
    void courseWithoutAssignmentsGivesAnEmptyList() {
        assertEquals(List.of(), service.getPublishedAssignments(STUDENT_ID, COURSE_ID));
    }

    @Test
    void courseTheStudentMayNotUseIsUnavailableAndNothingIsRead() {
        assertUnavailable("Course not found or unavailable.", () -> service.getPublishedAssignments(OTHER_STUDENT_ID, COURSE_ID));
        assertUnavailable("Course not found or unavailable.", () -> service.getPublishedAssignments(STUDENT_ID, 8L));
        assertUnavailable("Assignment not found or unavailable.",
                () -> service.getPublishedAssignment(OTHER_STUDENT_ID, COURSE_ID, 1L));

        verifyNoInteractions(assignments);
    }

    @Test
    void publishedAssignmentOfTheCourseIsShownWithItsCourse() {
        LmsAssignment published = assignment(course, 1, LmsContentStatus.PUBLISHED, "2026-10-20T18:29:00Z");
        found(published);

        assertEquals(new LmsCourseAssignmentDetailResponse(1L, COURSE_ID, "TST301", "Test Data Structures", "Assignment 1",
                        "Instructions 1", Instant.parse("2026-10-20T18:29:00Z"), new BigDecimal("20.00"),
                        Instant.parse("2026-10-01T09:00:00Z")),
                service.getPublishedAssignment(STUDENT_ID, COURSE_ID, 1L));
    }

    @Test
    void pastDuePublishedAssignmentCanStillBeOpened() {
        found(assignment(course, 1, LmsContentStatus.PUBLISHED, "2020-03-01T10:00:00Z"));

        assertEquals(1L, service.getPublishedAssignment(STUDENT_ID, COURSE_ID, 1L).id());
    }

    @Test
    void draftArchivedForeignAndMissingAssignmentsAllLookTheSame() {
        found(assignment(course, 2, LmsContentStatus.DRAFT, "2026-10-20T18:29:00Z"));
        found(assignment(course, 3, LmsContentStatus.ARCHIVED, "2026-10-20T18:29:00Z"));
        // Even if a lookup ever returned another course's assignment, it is not shown.
        found(assignment(otherCourse, 9, LmsContentStatus.PUBLISHED, "2026-10-20T18:29:00Z"));

        for (Long assignmentId : List.of(2L, 3L, 9L, 999L)) {
            assertUnavailable("Assignment not found or unavailable.",
                    () -> service.getPublishedAssignment(STUDENT_ID, COURSE_ID, assignmentId));
        }
    }
}
