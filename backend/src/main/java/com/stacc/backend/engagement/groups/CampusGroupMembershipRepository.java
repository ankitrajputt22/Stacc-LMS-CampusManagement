package com.stacc.backend.engagement.groups;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusGroupMembershipRepository extends JpaRepository<CampusGroupMembership, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CampusGroupMembership m where m.group.id = :groupId and m.account.id = :accountId "
            + "and m.status in (com.stacc.backend.engagement.groups.CampusGroupMembership.Status.PENDING, "
            + "com.stacc.backend.engagement.groups.CampusGroupMembership.Status.ACTIVE)")
    Optional<CampusGroupMembership> findCurrentForUpdate(@Param("groupId") Long groupId, @Param("accountId") Long accountId);

    @Query("select m from CampusGroupMembership m join fetch m.group where m.account.id = :accountId "
            + "and m.status in (com.stacc.backend.engagement.groups.CampusGroupMembership.Status.PENDING, "
            + "com.stacc.backend.engagement.groups.CampusGroupMembership.Status.ACTIVE)")
    List<CampusGroupMembership> findCurrentForAccount(@Param("accountId") Long accountId);

    @Query("select m from CampusGroupMembership m join fetch m.account where m.group.id = :groupId "
            + "and m.status in (com.stacc.backend.engagement.groups.CampusGroupMembership.Status.PENDING, "
            + "com.stacc.backend.engagement.groups.CampusGroupMembership.Status.ACTIVE) order by m.status, m.requestedAt")
    List<CampusGroupMembership> findCurrentForGroup(@Param("groupId") Long groupId);

    interface GroupCount {
        Long getGroupId();
        long getMembers();
    }

    @Query("select m.group.id as groupId, count(m) as members from CampusGroupMembership m "
            + "where m.status = com.stacc.backend.engagement.groups.CampusGroupMembership.Status.ACTIVE group by m.group.id")
    List<GroupCount> countActive();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CampusGroupMembership m where m.id = :id")
    Optional<CampusGroupMembership> findByIdForUpdate(@Param("id") Long id);
}
