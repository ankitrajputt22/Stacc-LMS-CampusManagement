package com.stacc.backend.projects;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMentorRepository extends JpaRepository<ProjectMentor, Long> {

    @Query("""
            select m from ProjectMentor m join fetch m.account where m.project.id in :projectIds
            and m.status in (com.stacc.backend.projects.ProjectMentor.Status.INVITED, com.stacc.backend.projects.ProjectMentor.Status.ACCEPTED)
            order by m.invitedAt, m.id
            """)
    List<ProjectMentor> findLiveFor(@Param("projectIds") Collection<Long> projectIds);
}
