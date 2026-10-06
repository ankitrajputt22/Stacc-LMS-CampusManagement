package com.stacc.backend.auth.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLoginId(String loginId);

    /** Loads the account together with its roles and their permissions in one query, for sign-in. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesAndPermissionsByLoginId(String loginId);
}
