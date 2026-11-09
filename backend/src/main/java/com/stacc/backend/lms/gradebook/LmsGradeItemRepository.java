package com.stacc.backend.lms.gradebook;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsGradeItemRepository extends JpaRepository<LmsGradeItem, Long> {

    /** Every grade item of the course, in the order they were made. */
    List<LmsGradeItem> findAllByLmsCourseIdOrderByIdAsc(Long lmsCourseId);

    /** The course's grade items read with a locking read, after the course row is locked, so the newest rows are seen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LmsGradeItem item where item.lmsCourse.id = :lmsCourseId order by item.id")
    List<LmsGradeItem> findAllForUpdate(@Param("lmsCourseId") Long lmsCourseId);

    /** The course of the item, read without loading it, so access is checked before the row is locked. */
    @Query("select item.lmsCourse.id from LmsGradeItem item where item.id = :itemId")
    Optional<Long> findLmsCourseIdById(@Param("itemId") Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LmsGradeItem item where item.id = :itemId")
    Optional<LmsGradeItem> findByIdForUpdate(@Param("itemId") Long itemId);
}
