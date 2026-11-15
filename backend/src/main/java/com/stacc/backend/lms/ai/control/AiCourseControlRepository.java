package com.stacc.backend.lms.ai.control;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiCourseControlRepository extends JpaRepository<AiCourseControl, Long> {

    Optional<AiCourseControl> findByLmsCourseId(Long lmsCourseId);

    @Query("select count(control) from AiCourseControl control where control.studentAiEnabled = false")
    long countTurnedOff();
}
