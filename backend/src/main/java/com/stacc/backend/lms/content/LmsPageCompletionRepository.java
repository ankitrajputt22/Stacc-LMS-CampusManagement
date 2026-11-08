package com.stacc.backend.lms.content;

import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsPageCompletionRepository extends JpaRepository<LmsPageCompletion, Long> {

    /** The pages this enrollment completed. */
    @Query("select completion.lmsTopic.id from LmsPageCompletion completion where completion.courseEnrollment.id = :courseEnrollmentId")
    Set<Long> findCompletedTopicIds(@Param("courseEnrollmentId") Long courseEnrollmentId);

    /**
     * The completion, read with a lock after the enrollment row lock. A locking read sees the latest committed row, so a
     * completion another transaction has just recorded is found instead of inserted again.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select completion from LmsPageCompletion completion
            where completion.courseEnrollment.id = :courseEnrollmentId and completion.lmsTopic.id = :lmsTopicId
            """)
    Optional<LmsPageCompletion> findForUpdate(@Param("courseEnrollmentId") Long courseEnrollmentId,
            @Param("lmsTopicId") Long lmsTopicId);
}
