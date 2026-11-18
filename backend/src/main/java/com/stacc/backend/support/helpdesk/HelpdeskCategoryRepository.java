package com.stacc.backend.support.helpdesk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {

    Optional<HelpdeskCategory> findByCode(String code);

    boolean existsByCode(String code);

    List<HelpdeskCategory> findAllByActiveTrueOrderByNameAscIdAsc();

    List<HelpdeskCategory> findAllByOrderByCodeAscIdAsc();
}
