package com.stacc.backend.lms.syllabus;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsSyllabusRepository extends JpaRepository<LmsSyllabus, Long> {

    Optional<LmsSyllabus> findByLmsCourseId(Long lmsCourseId);

    /**
     * The course's syllabus, read with a lock. A locking read sees the latest committed row, so a change made after the
     * course row lock finds a syllabus another transaction has just created, whatever the transaction read before.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select syllabus from LmsSyllabus syllabus where syllabus.lmsCourse.id = :lmsCourseId")
    Optional<LmsSyllabus> findByLmsCourseIdForUpdate(@Param("lmsCourseId") Long lmsCourseId);
}
