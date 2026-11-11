package com.stacc.backend.projects;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /** Requested and active places of these projects, with their accounts. */
    @Query("""
            select m from ProjectMember m join fetch m.account where m.project.id in :projectIds
            and m.status in (com.stacc.backend.projects.ProjectMember.Status.REQUESTED, com.stacc.backend.projects.ProjectMember.Status.ACTIVE)
            order by m.requestedAt, m.id
            """)
    List<ProjectMember> findLiveFor(@Param("projectIds") Collection<Long> projectIds);

    /** Active places of a project, read with a locking read after the project row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMember m where m.project.id = :projectId and m.status = com.stacc.backend.projects.ProjectMember.Status.ACTIVE")
    List<ProjectMember> findActiveForUpdate(@Param("projectId") Long projectId);

    @Query("select m.project.id from ProjectMember m where m.id = :id")
    Optional<Long> findProjectIdOf(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProjectMember m join fetch m.account where m.id = :id")
    Optional<ProjectMember> findByIdForUpdate(@Param("id") Long id);
}
