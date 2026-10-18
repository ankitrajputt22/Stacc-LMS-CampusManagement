package com.stacc.backend.lms.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * One LMS course, locked until the transaction ends ({@code SELECT ... FOR UPDATE}), so two lifecycle changes or
     * membership synchronizations of the same course never run at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lmsCourse from LmsCourse lmsCourse where lmsCourse.id = :lmsCourseId")
    Optional<LmsCourse> findByIdForUpdate(@Param("lmsCourseId") Long lmsCourseId);
}
