package com.stacc.backend.academic.program;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Search: programs, whatever their status, whose code or name contains the text, with their department. */
    @Query(value = """
            select program
            from Program program
            join fetch program.department
            where lower(program.code) like :contains escape '!' or lower(program.name) like :contains escape '!'
            order by case when lower(program.code) = :exact or lower(program.name) = :exact then 0 when lower(program.code) like :prefix escape '!' or lower(program.name) like :prefix escape '!' then 1 else 2 end, program.code, program.id
            """,
            countQuery = """
            select count(program)
            from Program program
            where lower(program.code) like :contains escape '!' or lower(program.name) like :contains escape '!'
            """)
    Page<Program> search(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);
}
