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

    List<LmsSubmissionAttempt> findAllBySubmissionIdOrderByAttemptNumberDesc(Long submissionId);

    @Query("""
            select attempt
            from LmsSubmissionAttempt attempt
            join fetch attempt.submission submission
            join fetch submission.assignment assignment
            join fetch assignment.lmsCourse
            where attempt.id = :attemptId
            """)
    Optional<LmsSubmissionAttempt> findWithAssignmentById(@Param("attemptId") Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LmsSubmissionAttempt attempt where attempt.id = :attemptId")
    Optional<LmsSubmissionAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    @Query("""
            select attempt
            from LmsSubmissionAttempt attempt
            join attempt.submission submission
            where submission.id in :submissionIds
              and attempt.attemptNumber = submission.latestAttemptNumber
            """)
    List<LmsSubmissionAttempt> findLatestBySubmissionIdIn(@Param("submissionIds") Collection<Long> submissionIds);

    @Query("""
            select attempt.submission.id as submissionId, count(attempt) as attemptCount
            from LmsSubmissionAttempt attempt
            where attempt.submission.id in :submissionIds
            group by attempt.submission.id
            """)
    List<AttemptCount> countBySubmissionIdIn(@Param("submissionIds") Collection<Long> submissionIds);

    interface AttemptCount {

        Long getSubmissionId();

        long getAttemptCount();
    }

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
