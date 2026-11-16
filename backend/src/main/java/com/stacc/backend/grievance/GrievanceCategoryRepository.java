package com.stacc.backend.grievance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GrievanceCategoryRepository extends JpaRepository<GrievanceCategory, Long> {

    List<GrievanceCategory> findAllByOrderByNameAsc();

    List<GrievanceCategory> findByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);
}
