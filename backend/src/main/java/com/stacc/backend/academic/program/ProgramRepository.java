package com.stacc.backend.academic.program;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByDepartmentIdAndCode(Long departmentId, String code);
}
