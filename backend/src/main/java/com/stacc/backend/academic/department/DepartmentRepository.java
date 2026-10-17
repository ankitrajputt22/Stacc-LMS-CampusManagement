package com.stacc.backend.academic.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    /** Every department, by code. */
    List<Department> findAllByOrderByCodeAscIdAsc();

    // These compare as the unique constraints do, in the column's collation.
    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
