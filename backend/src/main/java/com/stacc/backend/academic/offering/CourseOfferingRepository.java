package com.stacc.backend.academic.offering;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    Optional<CourseOffering> findByCourseIdAndSemesterId(Long courseId, Long semesterId);
}
