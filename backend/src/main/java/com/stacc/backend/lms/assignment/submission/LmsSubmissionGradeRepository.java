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

    /** The grade of one attempt, if it has one. */
    Optional<LmsSubmissionGrade> findByAttemptId(Long attemptId);

    /**
     * The same grade, locked until the transaction ends. A locking read always sees the newest committed
     * row, so a revision is never made from an older snapshot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grade from LmsSubmissionGrade grade where grade.attempt.id = :attemptId")
    Optional<LmsSubmissionGrade> findForUpdateByAttemptId(@Param("attemptId") Long attemptId);

    /** The grades of these attempts, in one query. Attempts without a grade simply have none here. */
    List<LmsSubmissionGrade> findAllByAttemptIdIn(Collection<Long> attemptIds);
}
