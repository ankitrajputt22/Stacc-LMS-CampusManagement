package com.stacc.backend.engagement.groups;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusGroupOfficerRepository extends JpaRepository<CampusGroupOfficer, Long> {

    @Query("select o from CampusGroupOfficer o join fetch o.account where o.group.id = :groupId and o.endedAt is null order by o.title, o.id")
    List<CampusGroupOfficer> findActiveByGroup(@Param("groupId") Long groupId);

    @Query("select o from CampusGroupOfficer o join fetch o.account join fetch o.group where o.endedAt is null order by o.group.id, o.title")
    List<CampusGroupOfficer> findAllActive();

    @Query("select count(o) > 0 from CampusGroupOfficer o where o.group.id = :groupId and o.account.id = :accountId and o.endedAt is null")
    boolean isOfficer(@Param("groupId") Long groupId, @Param("accountId") Long accountId);

    /** The group's current faculty advisor, the earliest assigned first. */
    @Query("select o from CampusGroupOfficer o join fetch o.account where o.group.id = :groupId and o.endedAt is null "
            + "and o.title = com.stacc.backend.engagement.groups.CampusGroupOfficer.Title.ADVISOR order by o.assignedAt asc")
    List<CampusGroupOfficer> findAdvisors(@Param("groupId") Long groupId);

    @Query("select o from CampusGroupOfficer o where o.id = :id")
    Optional<CampusGroupOfficer> findOne(@Param("id") Long id);
}
