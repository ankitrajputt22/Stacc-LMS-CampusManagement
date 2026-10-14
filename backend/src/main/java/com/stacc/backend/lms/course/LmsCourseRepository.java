package com.stacc.backend.lms.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsCourseRepository extends JpaRepository<LmsCourse, Long> {

    Optional<LmsCourse> findByCourseOfferingId(Long courseOfferingId);

    /** The LMS courses of these course offerings. An offering without an LMS course simply has none here. */
    List<LmsCourse> findAllByCourseOfferingIdIn(Collection<Long> courseOfferingIds);

    /**
     * One LMS course, loaded in a single query together with what a course page shows: the course
     * and its owning department, and the semester with its programme and academic session.
     */
    @Query("""
            select lmsCourse
            from LmsCourse lmsCourse
            join fetch lmsCourse.courseOffering courseOffering
            join fetch courseOffering.course course
            join fetch course.department
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            where lmsCourse.id = :lmsCourseId
            """)
    Optional<LmsCourse> findWithAcademicContextById(@Param("lmsCourseId") Long lmsCourseId);
}
