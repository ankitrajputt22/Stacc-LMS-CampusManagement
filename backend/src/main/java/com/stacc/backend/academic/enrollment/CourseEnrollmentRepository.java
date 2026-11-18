package com.stacc.backend.academic.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    Optional<CourseEnrollment> findBySemesterEnrollmentIdAndCourseOfferingId(
            Long semesterEnrollmentId, Long courseOfferingId);

    List<CourseEnrollment> findAllByCourseOfferingIdAndStatus(Long courseOfferingId, CourseEnrollmentStatus status);

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

    long countByStatus(CourseEnrollmentStatus status);

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

    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount account
            where courseEnrollment.courseOffering.id = :courseOfferingId
              and courseEnrollment.status = com.stacc.backend.academic.enrollment.CourseEnrollmentStatus.ENROLLED
              and semesterEnrollment.status = com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus.ENROLLED
              and studentProfile.status = com.stacc.backend.identity.student.StudentProfileStatus.ACTIVE
            order by account.loginId asc
            """)
    List<CourseEnrollment> findAllCurrentWithAccountByCourseOfferingId(@Param("courseOfferingId") Long courseOfferingId);

    @Query(value = "select count(*) from course_enrollments where course_offering_id = :courseOfferingId "
            + "and status = 'ENROLLED' for share", nativeQuery = true)
    long countEnrolledInOfferingForShare(@Param("courseOfferingId") Long courseOfferingId);

    @Query(value = """
            select enrollment
            from CourseEnrollment enrollment
            join fetch enrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile profile
            join fetch profile.userAccount account
            join fetch enrollment.courseOffering offering
            join fetch offering.course course
            join fetch offering.semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            left join fetch semesterEnrollment.section section
            where """ + MONITOR_FILTERS + """
            order by academicSession.startDate desc, program.code asc, semester.semesterNumber asc, course.code asc,
                     account.loginId asc, enrollment.id asc
            """,
            countQuery = """
            select count(enrollment)
            from CourseEnrollment enrollment
            join enrollment.semesterEnrollment semesterEnrollment
            join semesterEnrollment.studentProfile profile
            join profile.userAccount account
            join enrollment.courseOffering offering
            join offering.course course
            join offering.semester semester
            join semester.program program
            join semester.academicSession academicSession
            left join semesterEnrollment.section section
            where """ + MONITOR_FILTERS)
    Page<CourseEnrollment> searchForMonitor(
            @Param("academicSessionId") Long academicSessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("courseId") Long courseId,
            @Param("status") CourseEnrollmentStatus status,
            @Param("pattern") String pattern,
            Pageable page);

    String MONITOR_FILTERS = """
            (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:programId is null or program.id = :programId)
              and (:semesterId is null or semester.id = :semesterId)
              and (:sectionId is null or section.id = :sectionId)
              and (:courseId is null or course.id = :courseId)
              and (:status is null or enrollment.status = :status)
              and (:pattern is null
                   or lower(account.loginId) like :pattern escape '!'
                   or lower(profile.rollNumber) like :pattern escape '!'
                   or lower(course.code) like :pattern escape '!'
                   or exists (select person.id from PersonProfile person where person.userAccount = account
                              and (lower(person.fullName) like :pattern escape '!'
                                   or lower(person.preferredName) like :pattern escape '!')))
            """;

    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount userAccount
            join fetch courseEnrollment.courseOffering
            where userAccount.id = :userAccountId
            """)
    List<CourseEnrollment> findAllWithStudentByUserAccountId(@Param("userAccountId") Long userAccountId);

    @Query("""
            select courseEnrollment
            from CourseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount userAccount
            join fetch courseEnrollment.courseOffering courseOffering
            where userAccount.id = :userAccountId and courseOffering.id = :courseOfferingId
            """)
    List<CourseEnrollment> findAllWithStudentByUserAccountIdAndCourseOfferingId(
            @Param("userAccountId") Long userAccountId, @Param("courseOfferingId") Long courseOfferingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select courseEnrollment from CourseEnrollment courseEnrollment where courseEnrollment.id = :courseEnrollmentId")
    Optional<CourseEnrollment> findByIdForUpdate(@Param("courseEnrollmentId") Long courseEnrollmentId);

    @Query("""
            select courseEnrollment.courseOffering.id as courseOfferingId, courseEnrollment.status as status,
                   count(courseEnrollment) as total
            from CourseEnrollment courseEnrollment
            where courseEnrollment.courseOffering.id in :courseOfferingIds
            group by courseEnrollment.courseOffering.id, courseEnrollment.status
            """)
    List<OfferingStatusCount> countByOfferingAndStatus(@Param("courseOfferingIds") Collection<Long> courseOfferingIds);

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
