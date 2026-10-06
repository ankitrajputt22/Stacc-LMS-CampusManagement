package com.stacc.backend.academic.enrollment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterEnrollmentRepository extends JpaRepository<SemesterEnrollment, Long> {

    Optional<SemesterEnrollment> findByStudentProfileIdAndSemesterId(Long studentProfileId, Long semesterId);
}
