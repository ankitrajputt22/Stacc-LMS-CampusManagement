package com.stacc.backend.academic.enrollment;

import java.util.Collection;
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

    /** How many course enrollments have this status, such as every one still {@code ENROLLED}. */
    long countByStatus(CourseEnrollmentStatus status);

    /**
     * The course enrollments of one offering whose student currently takes part in it: the course enrollment and its
     * semester enrollment are both {@code ENROLLED} and the student profile is {@code ACTIVE}. The semester enrollment
     * and profile are loaded with each, in course-enrollment order.
     */
    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            where courseEnrollment.courseOffering.id = :courseOfferingId
              and courseEnrollment.status = com.stacc.backend.academic.enrollment.CourseEnrollmentStatus.ENROLLED
              and semesterEnrollment.status = com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus.ENROLLED
              and studentProfile.status = com.stacc.backend.identity.student.StudentProfileStatus.ACTIVE
            order by courseEnrollment.id asc
            """)
    List<CourseEnrollment> findAllCurrentByCourseOfferingId(@Param("courseOfferingId") Long courseOfferingId);

    /**
     * How many students are enrolled in one offering now, read with a shared lock. Called while the offering row is
     * locked, so two registrations for the last place cannot both see it free.
     */
    @Query(value = "select count(*) from course_enrollments where course_offering_id = :courseOfferingId "
            + "and status = 'ENROLLED' for share", nativeQuery = true)
    long countEnrolledInOfferingForShare(@Param("courseOfferingId") Long courseOfferingId);

    /** One enrollment, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select courseEnrollment from CourseEnrollment courseEnrollment where courseEnrollment.id = :courseEnrollmentId")
    Optional<CourseEnrollment> findByIdForUpdate(@Param("courseEnrollmentId") Long courseEnrollmentId);

    /** Reports: how many enrollments of each of these course offerings are in each status, in one grouped query. */
    @Query("""
            select courseEnrollment.courseOffering.id as courseOfferingId, courseEnrollment.status as status,
                   count(courseEnrollment) as total
            from CourseEnrollment courseEnrollment
            where courseEnrollment.courseOffering.id in :courseOfferingIds
            group by courseEnrollment.courseOffering.id, courseEnrollment.status
            """)
    List<OfferingStatusCount> countByOfferingAndStatus(@Param("courseOfferingIds") Collection<Long> courseOfferingIds);

    /**
     * Reports: how many course enrollments are in each status, for the offerings of the semesters that match every
     * filter that is not null.
     */
    @Query("""
            select courseEnrollment.status as status, count(courseEnrollment) as total
            from CourseEnrollment courseEnrollment
            join courseEnrollment.courseOffering offering
            join offering.semester semester
            where (:semesterId is null or semester.id = :semesterId)
              and (:programId is null or semester.program.id = :programId)
              and (:academicSessionId is null or semester.academicSession.id = :academicSessionId)
            group by courseEnrollment.status
            """)
    List<StatusCount> countByStatusWithin(@Param("semesterId") Long semesterId, @Param("programId") Long programId,
            @Param("academicSessionId") Long academicSessionId);

    interface OfferingStatusCount {
        Long getCourseOfferingId();

        CourseEnrollmentStatus getStatus();

        Long getTotal();
    }

    interface StatusCount {
        CourseEnrollmentStatus getStatus();

        Long getTotal();
    }

    /**
     * Whether a semester enrollment is still enrolled in a course offering of some section other than this one (any
     * section when it is null), which would no longer match the student's section after a move.
     */
    @Query("""
            select count(enrollment) > 0
            from CourseEnrollment enrollment
            join enrollment.courseOffering offering
            where enrollment.semesterEnrollment.id = :semesterEnrollmentId
              and enrollment.status = com.stacc.backend.academic.enrollment.CourseEnrollmentStatus.ENROLLED
              and offering.section is not null
              and (:sectionId is null or offering.section.id <> :sectionId)
            """)
    boolean existsEnrolledInOtherSectionOffering(@Param("semesterEnrollmentId") Long semesterEnrollmentId,
            @Param("sectionId") Long sectionId);

    /** The course enrollments of these semester enrollments with their offering and course, by course code. */
    @Query("""
            select enrollment
            from CourseEnrollment enrollment
            join fetch enrollment.courseOffering offering
            join fetch offering.course course
            left join fetch offering.section
            where enrollment.semesterEnrollment.id in :semesterEnrollmentIds
            order by course.code asc
            """)
    List<CourseEnrollment> findAllWithOfferingBySemesterEnrollmentIdIn(
            @Param("semesterEnrollmentIds") java.util.Collection<Long> semesterEnrollmentIds);
}
