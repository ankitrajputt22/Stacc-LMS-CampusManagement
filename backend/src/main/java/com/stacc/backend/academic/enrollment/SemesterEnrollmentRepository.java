package com.stacc.backend.academic.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.session.AcademicSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterEnrollmentRepository extends JpaRepository<SemesterEnrollment, Long> {

    Optional<SemesterEnrollment> findByStudentProfileIdAndSemesterId(Long studentProfileId, Long semesterId);

    @Query(value = "select count(*) from semester_enrollments where section_id = :sectionId and status = 'ENROLLED' for share",
            nativeQuery = true)
    long countEnrolledInSectionForShare(@Param("sectionId") Long sectionId);

    @Query("""
            select enrollment.section.id, count(enrollment)
            from SemesterEnrollment enrollment
            where enrollment.section is not null
            and enrollment.status = com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus.ENROLLED
            group by enrollment.section.id
            """)
    List<Object[]> countEnrolledBySection();

    long countByStatus(SemesterEnrollmentStatus status);

    @Query("""
            select enrollment
            from SemesterEnrollment enrollment
            join fetch enrollment.semester semester
            join fetch semester.program
            join fetch semester.academicSession academicSession
            left join fetch enrollment.section
            where enrollment.studentProfile.id = :studentProfileId
            order by academicSession.startDate desc, semester.semesterNumber desc, enrollment.id desc
            """)
    List<SemesterEnrollment> findAllWithContextByStudentProfileId(@Param("studentProfileId") Long studentProfileId);

    @Query("""
            select enrollment
            from SemesterEnrollment enrollment
            join fetch enrollment.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            join fetch enrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount
            left join fetch enrollment.section
            where enrollment.id = :semesterEnrollmentId
            """)
    Optional<SemesterEnrollment> findWithContextById(@Param("semesterEnrollmentId") Long semesterEnrollmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enrollment from SemesterEnrollment enrollment where enrollment.id = :semesterEnrollmentId")
    Optional<SemesterEnrollment> findByIdForUpdate(@Param("semesterEnrollmentId") Long semesterEnrollmentId);

    @Query("""
            select semester.id as semesterId, semester.academicSession.id as academicSessionId
            from SemesterEnrollment enrollment
            join enrollment.semester semester
            where enrollment.studentProfile.userAccount.id = :userAccountId
              and enrollment.status in :statuses
            """)
    List<EnrolledSemester> findEnrolledSemestersByUserAccountId(
            @Param("userAccountId") Long userAccountId, @Param("statuses") Collection<SemesterEnrollmentStatus> statuses);

    @Query("""
            select distinct academicSession
            from SemesterEnrollment enrollment
            join enrollment.semester semester
            join semester.academicSession academicSession
            where enrollment.studentProfile.id = :studentProfileId
              and enrollment.status = :status
            order by academicSession.startDate desc, academicSession.id desc
            """)
    List<AcademicSession> findSessionsOfStudentWithStatus(@Param("studentProfileId") Long studentProfileId,
            @Param("status") SemesterEnrollmentStatus status);

    @Query("""
            select count(enrollment) > 0
            from SemesterEnrollment enrollment
            where enrollment.studentProfile.id = :studentProfileId
              and enrollment.semester.academicSession.id = :academicSessionId
              and enrollment.status = :status
            """)
    boolean existsInSessionWithStatus(@Param("studentProfileId") Long studentProfileId,
            @Param("academicSessionId") Long academicSessionId, @Param("status") SemesterEnrollmentStatus status);

    interface EnrolledSemester {

        Long getSemesterId();

        Long getAcademicSessionId();
    }

    @Query("""
            select distinct studentProfile.userAccount.id
            from SemesterEnrollment enrollment
            join enrollment.studentProfile studentProfile
            join enrollment.semester semester
            where semester.academicSession.id = :academicSessionId
              and (:semesterId is null or semester.id = :semesterId)
              and enrollment.status in :statuses
            """)
    List<Long> findStudentAccountIdsInSession(@Param("academicSessionId") Long academicSessionId,
            @Param("semesterId") Long semesterId, @Param("statuses") Collection<SemesterEnrollmentStatus> statuses);

    @Query("""
            select enrollment.status as status, count(enrollment) as total
            from SemesterEnrollment enrollment
            join enrollment.semester semester
            where (:semesterId is null or semester.id = :semesterId)
              and (:programId is null or semester.program.id = :programId)
              and (:academicSessionId is null or semester.academicSession.id = :academicSessionId)
            group by enrollment.status
            """)
    List<StatusCount> countByStatusWithin(@Param("semesterId") Long semesterId, @Param("programId") Long programId,
            @Param("academicSessionId") Long academicSessionId);

    interface StatusCount {
        SemesterEnrollmentStatus getStatus();

        Long getTotal();
    }

    @Query("""
            select enrollment
            from SemesterEnrollment enrollment
            join fetch enrollment.semester semester
            join fetch semester.academicSession academicSession
            left join fetch enrollment.section
            where enrollment.studentProfile.id in :studentProfileIds
              and enrollment.status = com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus.ENROLLED
            order by academicSession.startDate desc, semester.semesterNumber desc
            """)
    List<SemesterEnrollment> findEnrolledWithContextByStudentProfileIdIn(
            @Param("studentProfileIds") Collection<Long> studentProfileIds);
}
