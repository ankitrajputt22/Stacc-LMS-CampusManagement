package com.stacc.backend.lms.course;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsCourseRepository extends JpaRepository<LmsCourse, Long> {

    Optional<LmsCourse> findByCourseOfferingId(Long courseOfferingId);
}
