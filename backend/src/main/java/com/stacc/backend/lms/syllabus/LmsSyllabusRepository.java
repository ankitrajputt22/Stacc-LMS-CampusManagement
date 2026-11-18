package com.stacc.backend.lms.syllabus;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsSyllabusRepository extends JpaRepository<LmsSyllabus, Long> {

    Optional<LmsSyllabus> findByLmsCourseId(Long lmsCourseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select syllabus from LmsSyllabus syllabus where syllabus.lmsCourse.id = :lmsCourseId")
    Optional<LmsSyllabus> findByLmsCourseIdForUpdate(@Param("lmsCourseId") Long lmsCourseId);
}
