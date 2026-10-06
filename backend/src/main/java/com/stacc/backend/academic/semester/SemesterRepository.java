package com.stacc.backend.academic.semester;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByProgramIdAndAcademicSessionIdAndSemesterNumber(
            Long programId, Long academicSessionId, int semesterNumber);
}
