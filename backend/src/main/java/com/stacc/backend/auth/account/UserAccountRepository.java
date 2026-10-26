package com.stacc.backend.auth.account;

import java.util.Collection;
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

    /** The IDs of the accounts in this status that hold the role. */
    @Query("select account.id from UserAccount account join account.roles role where role.name = :roleName and account.status = :status")
    List<Long> findIdsByRoleNameAndStatus(@Param("roleName") RoleName roleName, @Param("status") AccountStatus status);

    /** The IDs of the accounts in this status that hold any of the roles, each once. */
    @Query("select distinct account.id from UserAccount account join account.roles role where role.name in :roleNames and account.status = :status")
    List<Long> findDistinctIdsByRoleNamesAndStatus(@Param("roleNames") Collection<RoleName> roleNames,
            @Param("status") AccountStatus status);

    /** The login IDs of the accounts in this status that hold the role, in login ID order. */
    @Query("""
            select account.loginId
            from UserAccount account
            join account.roles role
            where role.name = :roleName and account.status = :status
            order by account.loginId
            """)
    List<String> findLoginIdsByRoleNameAndStatus(@Param("roleName") RoleName roleName,
            @Param("status") AccountStatus status);
}
