package com.stacc.backend.auth.account;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.role.RoleName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLoginId(String loginId);

    /** Loads the account together with its roles and their permissions in one query, for sign-in. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesAndPermissionsByLoginId(String loginId);

    /** The IDs of every account that holds the role. */
    @Query("select account.id from UserAccount account join account.roles role where role.name = :roleName")
    List<Long> findIdsByRoleName(@Param("roleName") RoleName roleName);
}
