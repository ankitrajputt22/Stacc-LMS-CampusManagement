package com.stacc.backend.lms.assignment.submission;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAssignmentSubmissionRepository extends JpaRepository<LmsAssignmentSubmission, Long> {

    /** The student's submission record for the assignment, if they have made one. For reading. */
    Optional<LmsAssignmentSubmission> findByAssignmentIdAndCourseEnrollmentId(Long assignmentId, Long courseEnrollmentId);

    /**
     * The same record, locked until the transaction ends. A locking read always sees the newest committed
     * row, so the next attempt number is worked out from the latest state, never from an older snapshot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from LmsAssignmentSubmission submission
            where submission.assignment.id = :assignmentId
              and submission.courseEnrollment.id = :courseEnrollmentId
            """)
    Optional<LmsAssignmentSubmission> findForUpdate(
            @Param("assignmentId") Long assignmentId, @Param("courseEnrollmentId") Long courseEnrollmentId);
}
