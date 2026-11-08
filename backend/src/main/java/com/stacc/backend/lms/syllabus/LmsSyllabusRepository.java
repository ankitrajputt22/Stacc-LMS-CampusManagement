package com.stacc.backend.lms.syllabus;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsSyllabusRepository extends JpaRepository<LmsSyllabus, Long> {

    Optional<LmsSyllabus> findByLmsCourseId(Long lmsCourseId);
}
