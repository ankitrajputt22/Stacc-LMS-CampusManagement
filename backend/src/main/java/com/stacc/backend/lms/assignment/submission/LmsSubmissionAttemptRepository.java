package com.stacc.backend.lms.assignment.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsSubmissionAttemptRepository extends JpaRepository<LmsSubmissionAttempt, Long> {

    /** Every attempt of one submission, newest first. */
    List<LmsSubmissionAttempt> findAllBySubmissionIdOrderByAttemptNumberDesc(Long submissionId);

    /** One attempt with its submission, assignment, and LMS course, which decide who may grade it. */
    @Query("""
            select attempt
            from LmsSubmissionAttempt attempt
            join fetch attempt.submission submission
            join fetch submission.assignment assignment
            join fetch assignment.lmsCourse
            where attempt.id = :attemptId
            """)
    Optional<LmsSubmissionAttempt> findWithAssignmentById(@Param("attemptId") Long attemptId);

    /**
     * One attempt row, locked until the transaction ends. The attempt itself never changes; the lock only
     * makes two graders of the same attempt take turns, so its first grade is created once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LmsSubmissionAttempt attempt where attempt.id = :attemptId")
    Optional<LmsSubmissionAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    /** The newest attempt of each of these submissions, in one query. */
    @Query("""
            select attempt
            from LmsSubmissionAttempt attempt
            join attempt.submission submission
            where submission.id in :submissionIds
              and attempt.attemptNumber = submission.latestAttemptNumber
            """)
    List<LmsSubmissionAttempt> findLatestBySubmissionIdIn(@Param("submissionIds") Collection<Long> submissionIds);

    /** How many attempts each of these submissions has, in one query. */
    @Query("""
            select attempt.submission.id as submissionId, count(attempt) as attemptCount
            from LmsSubmissionAttempt attempt
            where attempt.submission.id in :submissionIds
            group by attempt.submission.id
            """)
    List<AttemptCount> countBySubmissionIdIn(@Param("submissionIds") Collection<Long> submissionIds);

    /** The number of attempts of one submission. */
    interface AttemptCount {

        Long getSubmissionId();

        long getAttemptCount();
    }

    /**
     * The latest attempt of each submission that has no grade yet, for the published assignments of these LMS
     * courses: oldest hand-in first, then submission ID, one page of them with the total count. Only a submission's
     * latest attempt counts ({@code attemptNumber = latestAttemptNumber}), so an older attempt left ungraded never
     * puts a submission in the queue, and a graded older attempt never keeps one out of it. The submission's
     * assignment and student account are loaded with each.
     */
    @Query(value = """
            select attempt
            from LmsSubmissionAttempt attempt
            join fetch attempt.submission submission
            join fetch submission.assignment assignment
            join fetch submission.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
              and not exists (select grade.id from LmsSubmissionGrade grade where grade.attempt = attempt)
            order by attempt.submittedAt asc, submission.id asc
            """,
            countQuery = """
            select count(attempt)
            from LmsSubmissionAttempt attempt
            join attempt.submission submission
            join submission.assignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
              and not exists (select grade.id from LmsSubmissionGrade grade where grade.attempt = attempt)
            """)
    Page<LmsSubmissionAttempt> findLatestUngradedByLmsCourseIdIn(
            @Param("lmsCourseIds") Collection<Long> lmsCourseIds, Pageable page);

    /**
     * The count of {@link #findLatestUngradedByLmsCourseIdIn}, without loading anything: submissions of published
     * assignments whose latest attempt has no grade. Older ungraded attempts never count.
     */
    @Query("""
            select count(attempt)
            from LmsSubmissionAttempt attempt
            join attempt.submission submission
            join submission.assignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
              and not exists (select grade.id from LmsSubmissionGrade grade where grade.attempt = attempt)
            """)
    long countLatestUngradedByLmsCourseIdIn(@Param("lmsCourseIds") Collection<Long> lmsCourseIds);
}
