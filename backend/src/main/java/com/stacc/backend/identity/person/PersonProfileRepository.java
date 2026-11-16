package com.stacc.backend.identity.person;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {

    /** The account's profile, locked, so adding emergency contacts counts them one request at a time. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PersonProfile p where p.userAccount.id = :userAccountId")
    Optional<PersonProfile> findByUserAccountIdForUpdate(@org.springframework.data.repository.query.Param("userAccountId") Long userAccountId);

    Optional<PersonProfile> findByUserAccountId(Long userAccountId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndUserAccountIdNot(String email, Long userAccountId);

    /** The person profiles of these accounts, for showing names; accounts without one are simply missing. */
    List<PersonProfile> findAllByUserAccountIdIn(Collection<Long> userAccountIds);
}
