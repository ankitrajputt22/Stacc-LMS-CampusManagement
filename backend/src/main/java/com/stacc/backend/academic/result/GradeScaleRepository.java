package com.stacc.backend.academic.result;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeScaleRepository extends JpaRepository<GradeScaleEntry, String> {

    List<GradeScaleEntry> findAllByOrderByPositionAsc();
}
