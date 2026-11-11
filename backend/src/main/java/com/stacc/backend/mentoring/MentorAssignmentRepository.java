package com.stacc.backend.mentoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentorAssignmentRepository extends JpaRepository<MentorAssignment, Long> {

    @Query("select a from MentorAssignment a join fetch a.mentor join fetch a.mentee order by a.endedAt asc nulls first, a.mentor.loginId, a.mentee.loginId")
    List<MentorAssignment> findAllForAdmin();

    /** Pairings the account is the mentor or the mentee of, active first. */
    @Query("""
            select a from MentorAssignment a join fetch a.mentor join fetch a.mentee
            where a.mentor.id = :accountId or a.mentee.id = :accountId order by a.endedAt asc nulls first, a.startedAt desc
            """)
    List<MentorAssignment> findForAccount(@Param("accountId") Long accountId);
}
