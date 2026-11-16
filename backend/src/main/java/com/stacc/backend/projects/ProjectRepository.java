package com.stacc.backend.projects;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("select p from Project p join fetch p.lead where p.status <> com.stacc.backend.projects.Project.Status.ARCHIVED order by p.createdAt desc, p.id desc")
    List<Project> findListed();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p join fetch p.lead where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
