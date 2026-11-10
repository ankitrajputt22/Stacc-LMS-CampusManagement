package com.stacc.backend.engagement.groups;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupAnnouncementRepository extends JpaRepository<GroupAnnouncement, Long> {

    @Query("select a from GroupAnnouncement a where a.group.id = :groupId order by a.createdAt desc, a.id desc")
    List<GroupAnnouncement> findRecent(@Param("groupId") Long groupId, Pageable page);
}
