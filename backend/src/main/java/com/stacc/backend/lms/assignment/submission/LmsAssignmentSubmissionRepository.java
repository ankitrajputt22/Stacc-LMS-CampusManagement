package com.stacc.backend.lms.assignment.submission;

import java.util.List;
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

    /**
     * The submissions of one assignment that have at least one attempt, each loaded with the student's
     * account, whose login ID identifies them to faculty. Nothing else about the student is loaded.
     */
    @Query("""
            select submission
            from LmsAssignmentSubmission submission
            join fetch submission.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount
            where submission.assignment.id = :assignmentId
              and submission.latestAttemptNumber > 0
            """)
    List<LmsAssignmentSubmission> findAllWithStudentByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** One submission with its assignment and the student's account. */
    @Query("""
            select submission
            from LmsAssignmentSubmission submission
            join fetch submission.assignment assignment
            join fetch assignment.lmsCourse
            join fetch submission.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount
            where submission.id = :submissionId
            """)
    Optional<LmsAssignmentSubmission> findWithStudentById(@Param("submissionId") Long submissionId);
}
