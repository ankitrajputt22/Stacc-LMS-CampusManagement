package com.stacc.backend.academic.semester;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByProgramIdAndAcademicSessionIdAndSemesterNumber(
            Long programId, Long academicSessionId, int semesterNumber);

    /**
     * Every semester with its program, the program's department, and its academic session, in one query: the latest
     * session first, then program code, then semester number.
     */
    @Query("""
            select semester
            from Semester semester
            join fetch semester.program program
            join fetch program.department
            join fetch semester.academicSession academicSession
            order by academicSession.startDate desc, program.code asc, semester.semesterNumber asc, semester.id asc
            """)
    List<Semester> findAllWithContext();

    @Query("""
            select semester
            from Semester semester
            join fetch semester.program program
            join fetch program.department
            join fetch semester.academicSession
            where semester.id = :semesterId
            """)
    Optional<Semester> findWithContextById(@Param("semesterId") Long semesterId);

    /** The highest semester number a program already has, if it has any semester. */
    @Query("select max(semester.semesterNumber) from Semester semester where semester.program.id = :programId")
    Optional<Integer> findMaxSemesterNumberByProgramId(@Param("programId") Long programId);

    /**
     * Search: semesters, whatever their status, whose program code or name or academic session code contains the
     * text, or whose number is the number in the text (such as "3" or "semester 3"). The program and session are
     * loaded in the same query. Exact program or session codes first, then those that start with the text.
     */
    @Query(value = """
            select semester
            from Semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            where lower(program.code) like :contains escape '!'
               or lower(program.name) like :contains escape '!'
               or lower(academicSession.code) like :contains escape '!'
               or (:number is not null and semester.semesterNumber = :number)
            order by case when lower(program.code) = :exact or lower(academicSession.code) = :exact then 0 when lower(program.code) like :prefix escape '!' or lower(academicSession.code) like :prefix escape '!' then 1 else 2 end, academicSession.startDate desc, program.code,
                     semester.semesterNumber, semester.id
            """,
            countQuery = """
            select count(semester)
            from Semester semester
            join semester.program program
            join semester.academicSession academicSession
            where lower(program.code) like :contains escape '!'
               or lower(program.name) like :contains escape '!'
               or lower(academicSession.code) like :contains escape '!'
               or (:number is not null and semester.semesterNumber = :number)
            """)
    Page<Semester> search(@Param("number") Integer number, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);
}
