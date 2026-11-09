package com.stacc.backend.todo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.stacc.backend.lms.assignment.LmsAssignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only lookups for the To-Do list. */
public interface TodoAssignmentQueries extends Repository<LmsAssignment, Long> {

    /** Published assignments of the given courses, due from {@code from} on, that the student has not handed in. */
    @Query("""
            select a from LmsAssignment a join fetch a.lmsCourse
            where a.lmsCourse.id in :lmsCourseIds
              and a.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and a.dueAt >= :from
              and not exists (
                  select s.id from LmsAssignmentSubmission s
                  where s.assignment = a
                    and s.courseEnrollment.semesterEnrollment.studentProfile.userAccount.id = :accountId)
            order by a.dueAt asc, a.id asc
            """)
    List<LmsAssignment> findUnsubmitted(@Param("lmsCourseIds") Collection<Long> lmsCourseIds,
            @Param("accountId") Long accountId, @Param("from") Instant from, Pageable page);

    interface WaitingGrading {
        Long getAssignmentId();
        String getTitle();
        Long getLmsCourseId();
        long getWaiting();
    }

    /** Per published assignment of the given courses, how many submissions' latest attempts have no grade. */
    @Query("""
            select assignment.id as assignmentId, assignment.title as title, assignment.lmsCourse.id as lmsCourseId,
                   count(attempt) as waiting
            from LmsSubmissionAttempt attempt join attempt.submission submission join submission.assignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
              and not exists (select grade.id from LmsSubmissionGrade grade where grade.attempt = attempt)
            group by assignment.id, assignment.title, assignment.lmsCourse.id
            order by min(attempt.submittedAt) asc
            """)
    List<WaitingGrading> findWaitingGrading(@Param("lmsCourseIds") Collection<Long> lmsCourseIds, Pageable page);
}
