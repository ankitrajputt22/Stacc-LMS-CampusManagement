package com.stacc.backend.identity.person;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {

    Optional<PersonProfile> findByUserAccountId(Long userAccountId);

    boolean existsByEmail(String email);
}
