package com.stacc.backend.lms.assignment.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsSubmissionAttachmentRepository extends JpaRepository<LmsSubmissionAttachment, Long> {

    /** The attachments of these attempts, in one query, in the order they were added. */
    List<LmsSubmissionAttachment> findAllByAttemptIdInOrderByIdAsc(Collection<Long> attemptIds);

    /**
     * One attachment, loaded together with its attempt, submission, assignment, and the assignment's
     * LMS course, which decide who may download it.
     */
    @Query("""
            select attachment
            from LmsSubmissionAttachment attachment
            join fetch attachment.attempt attempt
            join fetch attempt.submission submission
            join fetch submission.assignment assignment
            join fetch assignment.lmsCourse
            where attachment.id = :attachmentId
            """)
    Optional<LmsSubmissionAttachment> findWithSubmissionById(@Param("attachmentId") Long attachmentId);
}
