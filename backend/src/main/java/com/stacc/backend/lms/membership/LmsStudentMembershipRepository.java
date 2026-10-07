package com.stacc.backend.lms.membership;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsStudentMembershipRepository extends JpaRepository<LmsStudentMembership, Long> {

    Optional<LmsStudentMembership> findByCourseEnrollmentId(Long courseEnrollmentId);

    /**
     * The memberships that the student behind one account has in one LMS course, loaded
     * together with the course enrollment, semester enrollment, and student profile they rest on.
     * Consistent data gives at most one. A list is returned because nothing in the database
     * forces that, and a stray second row must not turn a lookup into an error.
     */
    @Query("""
            select membership
            from LmsStudentMembership membership
            join fetch membership.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            where studentProfile.userAccount.id = :userAccountId
              and membership.lmsCourse.id = :lmsCourseId
            """)
    List<LmsStudentMembership> findAllByUserAccountIdAndLmsCourseId(
            @Param("userAccountId") Long userAccountId, @Param("lmsCourseId") Long lmsCourseId);

    /**
     * Every membership of the student behind one account, in one query, loaded together with
     * what is needed to decide access and to describe each LMS course: the enrollments and
     * student profile on one side, and the course, semester, programme, and session on the other.
     */
    @Query("""
            select membership
            from LmsStudentMembership membership
            join fetch membership.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch membership.lmsCourse lmsCourse
            join fetch lmsCourse.courseOffering courseOffering
            join fetch courseOffering.course
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            where studentProfile.userAccount.id = :userAccountId
            """)
    List<LmsStudentMembership> findAllWithCourseDetailsByUserAccountId(@Param("userAccountId") Long userAccountId);
}
