package com.stacc.backend.identity.admin;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AccountSearchRepository extends Repository<UserAccount, Long> {

    @Query("""
            select account from UserAccount account
            where (:roleName is null or exists (select role.id from UserAccount other join other.roles role
                                                where other = account and role.name = :roleName))
              and (:status is null or account.status = :status)
              and (:pattern is null
                   or lower(account.loginId) like :pattern escape '!'
                   or exists (select person.id from PersonProfile person
                              where person.userAccount = account
                                and (lower(person.fullName) like :pattern escape '!'
                                     or lower(person.preferredName) like :pattern escape '!'
                                     or lower(person.email) like :pattern escape '!')))
            order by account.loginId asc, account.id asc
            """)
    Page<UserAccount> search(@Param("roleName") RoleName roleName, @Param("status") AccountStatus status,
            @Param("pattern") String pattern, Pageable page);
}
