package com.stacc.backend.support.helpdesk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpdeskTeamRepository extends JpaRepository<HelpdeskTeam, Long> {

    Optional<HelpdeskTeam> findByCode(String code);

    List<HelpdeskTeam> findAllByOrderByNameAscIdAsc();
}
