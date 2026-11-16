package com.stacc.backend.academic.curriculum;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramCreditRequirementRepository extends JpaRepository<ProgramCreditRequirement, Long> {

    List<ProgramCreditRequirement> findAllByProgramIdOrderByIdAsc(Long programId);
}
