package com.stacc.backend.academic.offering;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    Optional<CourseOffering> findByCourseIdAndSemesterId(Long courseId, Long semesterId);

    /**
     * Course offerings with their course, semester, program, and academic session, in one query, narrowed by any of
     * the given IDs that are not null. The latest session comes first, then program code, semester number, and
     * course code.
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
            @Param("programId") Long programId);

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
}
