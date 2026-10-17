package com.stacc.backend.lms.assignment.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
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
}
