package com.stacc.backend.support.helpdesk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {

    Optional<HelpdeskCategory> findByCode(String code);

    boolean existsByCode(String code);

    /** The categories that can be chosen for a new ticket, by name. */
    List<HelpdeskCategory> findAllByActiveTrueOrderByNameAscIdAsc();

    /** Every category, active or not, by code. */
    List<HelpdeskCategory> findAllByOrderByCodeAscIdAsc();
}
