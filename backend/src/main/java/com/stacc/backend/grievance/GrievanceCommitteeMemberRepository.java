package com.stacc.backend.grievance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrievanceCommitteeMemberRepository extends JpaRepository<GrievanceCommitteeMember, Long> {

    @Query("select m from GrievanceCommitteeMember m join fetch m.account join fetch m.category where m.endedAt is null order by m.category.name, m.account.loginId")
    List<GrievanceCommitteeMember> findCurrent();

    /** Categories whose committee the account is on now. */
    @Query("select m.category.id from GrievanceCommitteeMember m where m.account.id = :accountId and m.endedAt is null")
    List<Long> findCategoryIdsFor(@Param("accountId") Long accountId);
}
