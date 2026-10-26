package com.stacc.backend.academic.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    /** Every department, by code. */
    List<Department> findAllByOrderByCodeAscIdAsc();

    // These compare as the unique constraints do, in the column's collation.
    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Search: departments, whatever their status, whose code or name contains the text. Exact codes or names first,
     * then those that start with the text, then the rest; within each, by code.
     */
    @Query(value = """
            select department
            from Department department
            where lower(department.code) like :contains escape '!' or lower(department.name) like :contains escape '!'
            order by case when lower(department.code) = :exact or lower(department.name) = :exact then 0 when lower(department.code) like :prefix escape '!' or lower(department.name) like :prefix escape '!' then 1 else 2 end, department.code, department.id
            """,
            countQuery = """
            select count(department)
            from Department department
            where lower(department.code) like :contains escape '!' or lower(department.name) like :contains escape '!'
            """)
    Page<Department> search(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);
}
