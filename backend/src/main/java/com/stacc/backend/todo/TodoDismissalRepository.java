package com.stacc.backend.todo;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoDismissalRepository extends JpaRepository<TodoDismissal, Long> {

    @Query("select d.itemKey from TodoDismissal d where d.userAccountId = :accountId")
    Set<String> findKeysByAccountId(@Param("accountId") Long accountId);

    boolean existsByUserAccountIdAndItemKey(Long userAccountId, String itemKey);
}
