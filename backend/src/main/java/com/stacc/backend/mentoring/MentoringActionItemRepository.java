package com.stacc.backend.mentoring;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentoringActionItemRepository extends JpaRepository<MentoringActionItem, Long> {

    @Query("select i from MentoringActionItem i where i.meeting.id in :meetingIds order by i.createdAt, i.id")
    List<MentoringActionItem> findForMeetings(@Param("meetingIds") Collection<Long> meetingIds);

    /** Open items the account owns in its active pairings, as mentor or as mentee, for the To-Do list. */
    @Query("""
            select i from MentoringActionItem i join fetch i.meeting m join fetch m.assignment a join fetch a.mentor join fetch a.mentee
            where i.completedAt is null and a.endedAt is null
            and ((i.owner = com.stacc.backend.mentoring.MentoringActionItem.Owner.MENTOR and a.mentor.id = :accountId)
              or (i.owner = com.stacc.backend.mentoring.MentoringActionItem.Owner.MENTEE and a.mentee.id = :accountId))
            order by case when i.dueAt is null then 1 else 0 end, i.dueAt, i.id
            """)
    List<MentoringActionItem> findOpenOwnedBy(@Param("accountId") Long accountId);
}
