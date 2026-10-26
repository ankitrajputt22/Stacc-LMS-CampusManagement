package com.stacc.backend.academic.offering;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    Optional<CourseOffering> findByCourseIdAndSemesterId(Long courseId, Long semesterId);

    /**
     * Course offerings with their course, semester, program, and academic session, in one query, narrowed by any of
     * the given IDs that are not null. The latest session comes first, then program code, semester number, and
     * course code. The admin list asks for every row ({@code Pageable.unpaged()}); a preview asks for the first page,
     * in the same order.
     */
    @Query("""
            select offering
            from CourseOffering offering
            join fetch offering.course course
            join fetch offering.semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            where (:semesterId is null or semester.id = :semesterId)
              and (:courseId is null or course.id = :courseId)
              and (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:programId is null or program.id = :programId)
            order by academicSession.startDate desc, program.code asc, semester.semesterNumber asc, course.code asc,
                     offering.id asc
            """)
    List<CourseOffering> findAllWithContext(
            @Param("semesterId") Long semesterId,
            @Param("courseId") Long courseId,
            @Param("academicSessionId") Long academicSessionId,
            @Param("programId") Long programId,
            Pageable page);

    @Query("""
            select offering
            from CourseOffering offering
            join fetch offering.course
            join fetch offering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            where offering.id = :courseOfferingId
            """)
    Optional<CourseOffering> findWithContextById(@Param("courseOfferingId") Long courseOfferingId);

    /**
     * Search: course offerings, whatever their status, whose course code or name, program code, or academic session
     * code contains the text, with the course, semester, program, and session loaded in the same query.
     */
    @Query(value = """
            select offering
            from CourseOffering offering
            join fetch offering.course course
            join fetch offering.semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            where lower(course.code) like :contains escape '!'
               or lower(course.name) like :contains escape '!'
               or lower(program.code) like :contains escape '!'
               or lower(academicSession.code) like :contains escape '!'
            order by case when lower(course.code) = :exact or lower(course.name) = :exact then 0 when lower(course.code) like :prefix escape '!' or lower(course.name) like :prefix escape '!' then 1 else 2 end, academicSession.startDate desc, course.code, offering.id
            """,
            countQuery = """
            select count(offering)
            from CourseOffering offering
            join offering.course course
            join offering.semester semester
            join semester.program program
            join semester.academicSession academicSession
            where lower(course.code) like :contains escape '!'
               or lower(course.name) like :contains escape '!'
               or lower(program.code) like :contains escape '!'
               or lower(academicSession.code) like :contains escape '!'
            """)
    Page<CourseOffering> search(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);
}
