package com.stacc.backend.lms.ai.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import org.junit.jupiter.api.Test;

class AiOpenAssignmentsTest {

    private static final Instant NOW = Instant.parse("2026-11-16T08:00:00Z");

    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAssignmentSubmissionRepository submissions = mock(LmsAssignmentSubmissionRepository.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final AiOpenAssignments open = new AiOpenAssignments(assignments, submissions, studentAccess);

    private static LmsAssignment assignment(long id, Instant dueAt) {
        LmsAssignment assignment = mock(LmsAssignment.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getDueAt()).thenReturn(dueAt);
        return assignment;
    }

    @Test
    void leavesOutPastAndHandedInWork() {
        LmsAssignment past = assignment(1, NOW.minusSeconds(60));
        LmsAssignment handedIn = assignment(2, NOW.plusSeconds(86_400));
        LmsAssignment open2 = assignment(3, NOW.plusSeconds(2 * 86_400));
        when(assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(any(), any())).thenReturn(List.of(past, handedIn, open2));
        CourseEnrollment enrollment = mock(CourseEnrollment.class);
        when(enrollment.getId()).thenReturn(40L);
        when(studentAccess.findAccessGrantingEnrollment(11L, 7L)).thenReturn(Optional.of(enrollment));
        LmsAssignmentSubmission submission = mock(LmsAssignmentSubmission.class);
        when(submission.getAssignment()).thenReturn(handedIn);
        when(submissions.findAllSubmitted(List.of(2L, 3L), List.of(40L))).thenReturn(List.of(submission));

        assertThat(open.stillToHandIn(11L, 7L, NOW)).containsExactly(open2);
    }

    @Test
    void asksNothingMoreWhenNothingIsDue() {
        when(assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(any(), any())).thenReturn(List.of());
        assertThat(open.stillToHandIn(11L, 7L, NOW)).isEmpty();
        verifyNoInteractions(studentAccess, submissions);
    }
}
