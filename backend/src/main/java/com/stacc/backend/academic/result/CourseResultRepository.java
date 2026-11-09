package com.stacc.backend.academic.result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseResultRepository extends JpaRepository<CourseResult, Long> {

    Optional<CourseResult> findByCourseEnrollmentId(Long courseEnrollmentId);

    /** The enrollment's result, locked until the transaction ends, so two saves of it take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from CourseResult result where result.courseEnrollment.id = :courseEnrollmentId")
    Optional<CourseResult> findForUpdateByCourseEnrollmentId(@Param("courseEnrollmentId") Long courseEnrollmentId);

    /** The results of these enrollments, in one query, for a course offering's roster. */
    List<CourseResult> findAllByCourseEnrollmentIdIn(Collection<Long> courseEnrollmentIds);

    /**
     * The published results of the student behind one account, in one query, with each course offering, course,
     * semester, programme, and academic session. Drafts are never included. The latest academic session comes
     * first, then the highest semester number, then course code.
     */
    @Query("""
            select result
            from CourseResult result
            join fetch result.courseEnrollment courseEnrollment
            join fetch courseEnrollment.courseOffering courseOffering
            join fetch courseOffering.course course
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession academicSession
            where courseEnrollment.semesterEnrollment.studentProfile.userAccount.id = :userAccountId
              and result.status = com.stacc.backend.academic.result.CourseResultStatus.PUBLISHED
            order by academicSession.startDate desc, semester.semesterNumber desc, course.code asc, result.id asc
            """)
    List<CourseResult> findAllPublishedByUserAccountId(@Param("userAccountId") Long userAccountId);

    /** Reports: how many results of each of these offerings are in each status, in one grouped query. */
    @Query("""
            select result.courseEnrollment.courseOffering.id as courseOfferingId, result.status as status,
                   count(result) as total
            from CourseResult result
            where result.courseEnrollment.courseOffering.id in :courseOfferingIds
            group by result.courseEnrollment.courseOffering.id, result.status
            """)
    List<OfferingStatusCount> countByOfferingAndStatus(@Param("courseOfferingIds") Collection<Long> courseOfferingIds);

    interface OfferingStatusCount {
        Long getCourseOfferingId();

        CourseResultStatus getStatus();

        Long getTotal();
    }
}
