package com.stacc.backend.campus.services;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusServiceDefinitionRepository extends JpaRepository<CampusServiceDefinition, Long> {

    boolean existsByCode(String code);

    Optional<CampusServiceDefinition> findByCode(String code);

    List<CampusServiceDefinition> findAllByOrderByCodeAscIdAsc();

    @Query("""
            select service
            from CampusServiceDefinition service
            where service.active = true
              and ((:student = true and service.availableToStudent = true)
                   or (:faculty = true and service.availableToFaculty = true))
            order by service.name asc, service.id asc
            """)
    List<CampusServiceDefinition> findAvailable(@Param("student") boolean student, @Param("faculty") boolean faculty);
}
