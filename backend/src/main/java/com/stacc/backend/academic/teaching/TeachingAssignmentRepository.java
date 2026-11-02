package com.stacc.backend.academic.teaching;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    Optional<TeachingAssignment> findByFacultyProfileIdAndCourseOfferingId(
            Long facultyProfileId, Long courseOfferingId);

    /** Every assignment of these offerings, active or not, with the faculty profile and account, for admin lists. */
    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile facultyProfile
            join fetch facultyProfile.userAccount account
            join fetch facultyProfile.department
            where assignment.courseOffering.id in :courseOfferingIds
            order by assignment.status asc, assignment.role asc, account.loginId asc
            """)
    List<TeachingAssignment> findAllWithFacultyByCourseOfferingIdIn(
            @Param("courseOfferingIds") Collection<Long> courseOfferingIds);

    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile facultyProfile
            join fetch facultyProfile.userAccount
            join fetch facultyProfile.department
            join fetch assignment.courseOffering
            where assignment.id = :teachingAssignmentId
            """)
    Optional<TeachingAssignment> findWithFacultyById(@Param("teachingAssignmentId") Long teachingAssignmentId);

    /** Whether the offering has another active assignment with this role, such as a second primary faculty member. */
    boolean existsByCourseOfferingIdAndRoleAndStatusAndIdNot(Long courseOfferingId, TeachingRole role,
            TeachingAssignmentStatus status, Long teachingAssignmentId);

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
            left join fetch courseOffering.section
            where facultyProfile.userAccount.id = :userAccountId
            """)
    List<TeachingAssignment> findAllWithCourseDetailsByUserAccountId(@Param("userAccountId") Long userAccountId);

    /**
     * The teaching assignments of the offerings in one academic session, and, when a semester is given, in that
     * semester only, with each faculty profile. Whether an assignment still counts is decided by the caller.
     */
    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile
            join assignment.courseOffering courseOffering
            join courseOffering.semester semester
            where semester.academicSession.id = :academicSessionId
              and (:semesterId is null or semester.id = :semesterId)
            """)
    List<TeachingAssignment> findAllWithFacultyInSession(@Param("academicSessionId") Long academicSessionId,
            @Param("semesterId") Long semesterId);

    /** A faculty profile's active assignments with each offering's course, semester, program, session, and section. */
    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.courseOffering offering
            join fetch offering.course course
            join fetch offering.semester semester
            join fetch semester.program
            join fetch semester.academicSession academicSession
            left join fetch offering.section
            where assignment.facultyProfile.id = :facultyProfileId
              and assignment.status = com.stacc.backend.academic.teaching.TeachingAssignmentStatus.ACTIVE
            order by academicSession.startDate desc, course.code asc
            """)
    List<TeachingAssignment> findActiveWithOfferingByFacultyProfileId(@Param("facultyProfileId") Long facultyProfileId);

    /** How many active assignments each of these faculty profiles has, as pairs of profile ID and count. */
    @Query("""
            select assignment.facultyProfile.id, count(assignment)
            from TeachingAssignment assignment
            where assignment.facultyProfile.id in :facultyProfileIds
              and assignment.status = com.stacc.backend.academic.teaching.TeachingAssignmentStatus.ACTIVE
            group by assignment.facultyProfile.id
            """)
    List<Object[]> countActiveByFacultyProfileIdIn(@Param("facultyProfileIds") Collection<Long> facultyProfileIds);
}
