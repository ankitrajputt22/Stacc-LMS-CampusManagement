package com.stacc.backend.lms.outcome;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsCourseOutcomeRepository extends JpaRepository<LmsCourseOutcome, Long> {

    /** Every outcome of the course, archived ones included, by number. */
    List<LmsCourseOutcome> findAllByLmsCourseIdOrderByNumberAsc(Long lmsCourseId);

    /** The course's active outcomes, by number. */
    List<LmsCourseOutcome> findAllByLmsCourseIdAndStatusOrderByNumberAsc(Long lmsCourseId, LmsOutcomeStatus status);

    /**
     * Every outcome of the course, read with a lock after the course row lock. A locking read sees the latest committed
     * rows, so the next number never repeats one another transaction has just used.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outcome from LmsCourseOutcome outcome where outcome.lmsCourse.id = :lmsCourseId")
    List<LmsCourseOutcome> findAllByLmsCourseIdForUpdate(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select outcome from LmsCourseOutcome outcome join fetch outcome.lmsCourse where outcome.id = :outcomeId")
    Optional<LmsCourseOutcome> findWithCourseById(@Param("outcomeId") Long outcomeId);

    List<LmsCourseOutcome> findAllByIdIn(Collection<Long> ids);
}
