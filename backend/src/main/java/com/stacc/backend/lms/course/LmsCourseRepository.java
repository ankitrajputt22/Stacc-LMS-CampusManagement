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
     * The LMS courses of the same catalog course in other offerings that this account taught (any teaching assignment,
     * active or ended), whatever their LMS status, the latest term first: the courses faculty may copy content from.
     */
    @Query("""
            select lmsCourse
            from LmsCourse lmsCourse
            join fetch lmsCourse.courseOffering courseOffering
            join fetch courseOffering.course course
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession academicSession
            left join fetch courseOffering.section
            where course.id = :courseId
              and courseOffering.id <> :courseOfferingId
              and exists (
                  select assignment.id from TeachingAssignment assignment
                  where assignment.courseOffering = courseOffering
                    and assignment.facultyProfile.userAccount.id = :userAccountId)
            order by academicSession.startDate desc, semester.semesterNumber desc, lmsCourse.id desc
            """)
    List<LmsCourse> findCopySources(@Param("courseId") Long courseId, @Param("courseOfferingId") Long courseOfferingId,
            @Param("userAccountId") Long userAccountId);

    /** The LMS courses of these course offerings, each with the same academic details as one course page. */
    @Query("""
            select lmsCourse
            from LmsCourse lmsCourse
            join fetch lmsCourse.courseOffering courseOffering
            join fetch courseOffering.course course
            join fetch course.department
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            left join fetch courseOffering.section
            where courseOffering.id in :courseOfferingIds
            """)
    List<LmsCourse> findAllWithAcademicContextByCourseOfferingIdIn(
            @Param("courseOfferingIds") Collection<Long> courseOfferingIds);

    /**
     * One LMS course, locked until the transaction ends ({@code SELECT ... FOR UPDATE}), so two lifecycle changes of the
     * same course never run at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lmsCourse from LmsCourse lmsCourse where lmsCourse.id = :lmsCourseId")
    Optional<LmsCourse> findByIdForUpdate(@Param("lmsCourseId") Long lmsCourseId);
}
