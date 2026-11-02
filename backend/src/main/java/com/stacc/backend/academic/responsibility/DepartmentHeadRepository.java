package com.stacc.backend.academic.responsibility;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentHeadRepository extends JpaRepository<DepartmentHead, Long> {

    /** The department's active term, with the account and its roles. */
    @Query("""
            select head
            from DepartmentHead head
            join fetch head.userAccount account
            left join fetch account.roles
            where head.department.id = :departmentId
              and head.status = com.stacc.backend.academic.responsibility.DepartmentHeadStatus.ACTIVE
            """)
    Optional<DepartmentHead> findActiveByDepartmentId(@Param("departmentId") Long departmentId);

    /** Every active term, with the account and its roles. */
    @Query("""
            select distinct head
            from DepartmentHead head
            join fetch head.userAccount account
            left join fetch account.roles
            where head.status = com.stacc.backend.academic.responsibility.DepartmentHeadStatus.ACTIVE
            """)
    List<DepartmentHead> findAllActive();

    /** Every term of the department, the latest first, with the account and its roles. */
    @Query("""
            select distinct head
            from DepartmentHead head
            join fetch head.userAccount account
            left join fetch account.roles
            where head.department.id = :departmentId
            order by head.startedAt desc, head.id desc
            """)
    List<DepartmentHead> findAllByDepartmentId(@Param("departmentId") Long departmentId);
}
