package com.stacc.backend.academic.teaching;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    Optional<TeachingAssignment> findByFacultyProfileIdAndCourseOfferingId(
            Long facultyProfileId, Long courseOfferingId);
}
