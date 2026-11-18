package com.stacc.backend.academic.offering;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offering from CourseOffering offering where offering.id = :courseOfferingId")
    Optional<CourseOffering> findByIdForUpdate(@Param("courseOfferingId") Long courseOfferingId);

    Optional<CourseOffering> findByCourseIdAndSemesterIdAndSectionIsNull(Long courseId, Long semesterId);

    Optional<CourseOffering> findByCourseIdAndSemesterIdAndSectionId(Long courseId, Long semesterId, Long sectionId);

    boolean existsByCourseIdAndSemesterIdAndSectionIsNotNull(Long courseId, Long semesterId);

    @Query("""
            select offering
            from CourseOffering offering
            join fetch offering.course course
            join fetch offering.semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            left join fetch offering.section section
            where (:semesterId is null or semester.id = :semesterId)
              and (:courseId is null or course.id = :courseId)
              and (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:programId is null or program.id = :programId)
            order by academicSession.startDate desc, program.code asc, semester.semesterNumber asc, course.code asc,
                     section.code asc, offering.id asc
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
            left join fetch offering.section
            where offering.id = :courseOfferingId
            """)
    Optional<CourseOffering> findWithContextById(@Param("courseOfferingId") Long courseOfferingId);

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

    @Query("""
            select count(offering)
            from CourseOffering offering
            join offering.semester semester
            where (:semesterId is null or semester.id = :semesterId)
              and (:programId is null or semester.program.id = :programId)
              and (:academicSessionId is null or semester.academicSession.id = :academicSessionId)
            """)
    long countWithin(@Param("semesterId") Long semesterId, @Param("programId") Long programId,
            @Param("academicSessionId") Long academicSessionId);
}
