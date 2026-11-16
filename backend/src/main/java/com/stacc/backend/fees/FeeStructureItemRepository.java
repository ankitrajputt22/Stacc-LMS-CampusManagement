package com.stacc.backend.fees;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureItemRepository extends JpaRepository<FeeStructureItem, Long> {

    List<FeeStructureItem> findAllByFeeStructureIdOrderByIdAsc(Long feeStructureId);
}
