package com.stacc.backend.fees;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

    Optional<FeeStructure> findByProgramIdAndAcademicSessionId(Long programId, Long academicSessionId);
}
