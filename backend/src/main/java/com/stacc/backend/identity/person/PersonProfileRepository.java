package com.stacc.backend.identity.person;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {

    Optional<PersonProfile> findByUserAccountId(Long userAccountId);

    boolean existsByEmail(String email);

    /** The person profiles of these accounts, for showing names; accounts without one are simply missing. */
    List<PersonProfile> findAllByUserAccountIdIn(Collection<Long> userAccountIds);
}
