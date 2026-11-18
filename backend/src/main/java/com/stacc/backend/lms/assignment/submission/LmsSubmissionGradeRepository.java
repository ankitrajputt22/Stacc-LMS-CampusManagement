package com.stacc.backend.lms.assignment.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsSubmissionGradeRepository extends JpaRepository<LmsSubmissionGrade, Long> {

    @Query("""
            select grade from LmsSubmissionGrade grade join fetch grade.attempt attempt join fetch attempt.submission submission
            where submission.assignment.id in :assignmentIds and submission.courseEnrollment.id in :courseEnrollmentIds
              and attempt.attemptNumber = submission.latestAttemptNumber
            """)
    List<LmsSubmissionGrade> findLatestAttemptGrades(@Param("assignmentIds") Collection<Long> assignmentIds,
            @Param("courseEnrollmentIds") Collection<Long> courseEnrollmentIds);

    Optional<LmsSubmissionGrade> findByAttemptId(Long attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grade from LmsSubmissionGrade grade where grade.attempt.id = :attemptId")
    Optional<LmsSubmissionGrade> findForUpdateByAttemptId(@Param("attemptId") Long attemptId);

    List<LmsSubmissionGrade> findAllByAttemptIdIn(Collection<Long> attemptIds);
}
