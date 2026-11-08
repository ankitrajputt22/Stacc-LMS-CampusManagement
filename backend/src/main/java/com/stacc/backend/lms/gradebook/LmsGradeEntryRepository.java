package com.stacc.backend.lms.gradebook;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsGradeEntryRepository extends JpaRepository<LmsGradeEntry, Long> {

    List<LmsGradeEntry> findAllByItemIdIn(Collection<Long> itemIds);

    List<LmsGradeEntry> findAllByItemIdInAndCourseEnrollmentId(Collection<Long> itemIds, Long courseEnrollmentId);

    /** One student's entry, read with a locking read while the item row is locked, so an entry just saved is seen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from LmsGradeEntry entry where entry.item.id = :itemId and entry.courseEnrollment.id = :courseEnrollmentId")
    Optional<LmsGradeEntry> findForUpdate(@Param("itemId") Long itemId, @Param("courseEnrollmentId") Long courseEnrollmentId);

    /** Every entry of an item, read with a locking read while the item row is locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from LmsGradeEntry entry where entry.item.id = :itemId")
    List<LmsGradeEntry> findAllForUpdate(@Param("itemId") Long itemId);
}
