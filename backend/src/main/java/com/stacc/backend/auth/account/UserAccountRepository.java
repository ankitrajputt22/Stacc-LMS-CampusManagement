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

    boolean existsByLoginId(String loginId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :accountId")
    Optional<UserAccount> findByIdForUpdate(@Param("accountId") Long accountId);


    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesAndPermissionsByLoginId(String loginId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesAndPermissionsById(Long id);

    @Query("select account.id from UserAccount account join account.roles role where role.name = :roleName")
    List<Long> findIdsByRoleName(@Param("roleName") RoleName roleName);

    @Query("select account.id from UserAccount account join account.roles role where role.name = :roleName and account.status = :status")
    List<Long> findIdsByRoleNameAndStatus(@Param("roleName") RoleName roleName, @Param("status") AccountStatus status);

    @Query("select distinct account.id from UserAccount account join account.roles role where role.name in :roleNames and account.status = :status")
    List<Long> findDistinctIdsByRoleNamesAndStatus(@Param("roleNames") Collection<RoleName> roleNames,
            @Param("status") AccountStatus status);

    @Query("""
            select account
            from UserAccount account
            join account.roles role
            where role.name = :roleName and account.status = :status
            order by account.loginId
            """)
    List<UserAccount> findAllByRoleNameAndStatus(@Param("roleName") RoleName roleName,
            @Param("status") AccountStatus status);

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
