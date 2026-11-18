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

    boolean existsByCourseOfferingIdAndRoleAndStatusAndIdNot(Long courseOfferingId, TeachingRole role,
            TeachingAssignmentStatus status, Long teachingAssignmentId);

    @Query("""
            select assignment
            from TeachingAssignment assignment
            join fetch assignment.facultyProfile facultyProfile
            where facultyProfile.userAccount.id = :userAccountId
              and assignment.courseOffering.id = :courseOfferingId
            """)
    List<TeachingAssignment> findAllByUserAccountIdAndCourseOfferingId(
            @Param("userAccountId") Long userAccountId, @Param("courseOfferingId") Long courseOfferingId);

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

    @Query("""
            select assignment.facultyProfile.id, count(assignment)
            from TeachingAssignment assignment
            where assignment.facultyProfile.id in :facultyProfileIds
              and assignment.status = com.stacc.backend.academic.teaching.TeachingAssignmentStatus.ACTIVE
            group by assignment.facultyProfile.id
            """)
    List<Object[]> countActiveByFacultyProfileIdIn(@Param("facultyProfileIds") Collection<Long> facultyProfileIds);
}
