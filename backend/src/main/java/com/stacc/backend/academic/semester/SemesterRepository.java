package com.stacc.backend.academic.semester;

import java.util.List;
import java.util.Optional;

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
}
