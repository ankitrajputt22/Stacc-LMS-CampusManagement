package com.stacc.backend.lms.ai.mastery;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The assignments a student still has to hand in, for recommendations and study plans: the course's published
 * assignments due after now, without an attempt from the student's access-granting course enrollment, earliest first.
 * Only reads; work already handed in needs no more preparation.
 */
@Component
public class AiOpenAssignments {

    private final LmsAssignmentRepository assignments;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsStudentAccessService studentAccess;

    public AiOpenAssignments(LmsAssignmentRepository assignments, LmsAssignmentSubmissionRepository submissions,
            LmsStudentAccessService studentAccess) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.studentAccess = studentAccess;
    }

    @Transactional(readOnly = true)
    public List<LmsAssignment> stillToHandIn(Long accountId, Long lmsCourseId, Instant now) {
        List<LmsAssignment> due = assignments.findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(lmsCourseId,
                List.of(LmsContentStatus.PUBLISHED)).stream().filter(assignment -> assignment.getDueAt().isAfter(now)).toList();
        if (due.isEmpty()) {
            return due;
        }
        return studentAccess.findAccessGrantingEnrollment(accountId, lmsCourseId).map(enrollment -> {
            Set<Long> handedIn = submissions.findAllSubmitted(due.stream().map(LmsAssignment::getId).toList(), List.of(enrollment.getId()))
                    .stream().map(LmsAssignmentSubmission::getAssignment).map(LmsAssignment::getId).collect(Collectors.toSet());
            return due.stream().filter(assignment -> !handedIn.contains(assignment.getId())).toList();
        }).orElse(List.of());
    }
}
