package com.stacc.backend.academic.program;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByDepartmentIdAndCode(Long departmentId, String code);

    /** Every program with its department, in one query, by department code and then program code. */
    @Query("""
            select program
            from Program program
            join fetch program.department department
            order by department.code asc, program.code asc, program.id asc
            """)
    List<Program> findAllWithDepartment();

    @Query("select program from Program program join fetch program.department where program.id = :programId")
    Optional<Program> findWithDepartmentById(@Param("programId") Long programId);

    // These compare as the unique constraints do, in the columns' collation.
    boolean existsByDepartmentIdAndCode(Long departmentId, String code);

    boolean existsByDepartmentIdAndName(Long departmentId, String name);

    boolean existsByDepartmentIdAndNameAndIdNot(Long departmentId, String name, Long id);
}
