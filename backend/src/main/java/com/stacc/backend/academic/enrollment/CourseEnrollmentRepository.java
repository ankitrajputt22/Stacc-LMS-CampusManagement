package com.stacc.backend.academic.enrollment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    Optional<CourseEnrollment> findBySemesterEnrollmentIdAndCourseOfferingId(
            Long semesterEnrollmentId, Long courseOfferingId);

    /** The enrollments of one course offering in one status, such as everyone currently enrolled. */
    List<CourseEnrollment> findAllByCourseOfferingIdAndStatus(Long courseOfferingId, CourseEnrollmentStatus status);

    /** Every enrollment of one course offering, whatever its status, with the student's account, in student ID order. */
    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount userAccount
            where courseEnrollment.courseOffering.id = :courseOfferingId
            order by userAccount.loginId asc, courseEnrollment.id asc
            """)
    List<CourseEnrollment> findAllWithStudentByCourseOfferingId(@Param("courseOfferingId") Long courseOfferingId);

    /** A semester enrollment's course enrollments with each offering and course, by course code. */
    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.courseOffering courseOffering
            join fetch courseOffering.course course
            where courseEnrollment.semesterEnrollment.id = :semesterEnrollmentId
            order by course.code asc, courseEnrollment.id asc
            """)
    List<CourseEnrollment> findAllWithOfferingBySemesterEnrollmentId(@Param("semesterEnrollmentId") Long semesterEnrollmentId);

    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.courseOffering courseOffering
            join fetch courseOffering.course
            where courseEnrollment.id = :courseEnrollmentId
            """)
    Optional<CourseEnrollment> findWithOfferingById(@Param("courseEnrollmentId") Long courseEnrollmentId);

    boolean existsBySemesterEnrollmentIdAndStatus(Long semesterEnrollmentId, CourseEnrollmentStatus status);

    /** One enrollment, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select courseEnrollment from CourseEnrollment courseEnrollment where courseEnrollment.id = :courseEnrollmentId")
    Optional<CourseEnrollment> findByIdForUpdate(@Param("courseEnrollmentId") Long courseEnrollmentId);
}
