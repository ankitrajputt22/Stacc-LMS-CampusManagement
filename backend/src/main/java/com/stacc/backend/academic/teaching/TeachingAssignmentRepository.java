package com.stacc.backend.academic.teaching;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    Optional<TeachingAssignment> findByFacultyProfileIdAndCourseOfferingId(
            Long facultyProfileId, Long courseOfferingId);

    /**
     * The teaching assignments that the faculty member behind one account has for one course
     * offering, loaded together with the faculty profile. Consistent data gives at most one. A
     * list is returned because a lookup must not fail on a stray second row.
     */
    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile facultyProfile
            where facultyProfile.userAccount.id = :userAccountId
              and assignment.courseOffering.id = :courseOfferingId
            """)
    List<TeachingAssignment> findAllByUserAccountIdAndCourseOfferingId(
            @Param("userAccountId") Long userAccountId, @Param("courseOfferingId") Long courseOfferingId);

    /**
     * Every teaching assignment of the faculty member behind one account, in one query, loaded
     * together with the faculty profile and the course, semester, programme, and session of each
     * course offering. Whether an assignment still counts is decided by the caller.
     */
    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile facultyProfile
            join fetch assignment.courseOffering courseOffering
            join fetch courseOffering.course
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            where facultyProfile.userAccount.id = :userAccountId
            """)
    List<TeachingAssignment> findAllWithCourseDetailsByUserAccountId(@Param("userAccountId") Long userAccountId);
}
