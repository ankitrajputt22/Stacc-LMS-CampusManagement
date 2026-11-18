package com.stacc.backend.lms.assignment.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAssignmentSubmissionRepository extends JpaRepository<LmsAssignmentSubmission, Long> {

    @Query("""
            select submission from LmsAssignmentSubmission submission
            where submission.assignment.id in :assignmentIds and submission.courseEnrollment.id in :courseEnrollmentIds
              and submission.latestAttemptNumber > 0
            """)
    List<LmsAssignmentSubmission> findAllSubmitted(@Param("assignmentIds") Collection<Long> assignmentIds,
            @Param("courseEnrollmentIds") Collection<Long> courseEnrollmentIds);

    Optional<LmsAssignmentSubmission> findByAssignmentIdAndCourseEnrollmentId(Long assignmentId, Long courseEnrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from LmsAssignmentSubmission submission
            where submission.assignment.id = :assignmentId
              and submission.courseEnrollment.id = :courseEnrollmentId
            """)
    Optional<LmsAssignmentSubmission> findForUpdate(
            @Param("assignmentId") Long assignmentId, @Param("courseEnrollmentId") Long courseEnrollmentId);

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

    @Query("""
            select count(submission)
            from LmsAssignmentSubmission submission
            where submission.assignment.lmsCourse.id = :lmsCourseId
              and submission.assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and exists (select attempt.id from LmsSubmissionAttempt attempt where attempt.submission = submission)
            """)
    long countSubmittedForPublishedAssignments(@Param("lmsCourseId") Long lmsCourseId);
}
