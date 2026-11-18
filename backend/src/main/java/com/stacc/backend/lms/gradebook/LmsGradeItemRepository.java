package com.stacc.backend.lms.gradebook;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsGradeItemRepository extends JpaRepository<LmsGradeItem, Long> {

    List<LmsGradeItem> findAllByLmsCourseIdOrderByIdAsc(Long lmsCourseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LmsGradeItem item where item.lmsCourse.id = :lmsCourseId order by item.id")
    List<LmsGradeItem> findAllForUpdate(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select item.lmsCourse.id from LmsGradeItem item where item.id = :itemId")
    Optional<Long> findLmsCourseIdById(@Param("itemId") Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LmsGradeItem item where item.id = :itemId")
    Optional<LmsGradeItem> findByIdForUpdate(@Param("itemId") Long itemId);
}
