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
}
