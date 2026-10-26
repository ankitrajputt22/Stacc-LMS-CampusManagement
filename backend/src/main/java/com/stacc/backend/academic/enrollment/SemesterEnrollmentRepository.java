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

    /** How many semester enrollments have this status, such as every one still {@code ENROLLED}. */
    long countByStatus(SemesterEnrollmentStatus status);

    /** A student's semester enrollments with each semester, program, and session: the latest session first. */
    @Query("""
            select enrollment
            from SemesterEnrollment enrollment
            join fetch enrollment.semester semester
            join fetch semester.program
            join fetch semester.academicSession academicSession
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
            where enrollment.id = :semesterEnrollmentId
            """)
    Optional<SemesterEnrollment> findWithContextById(@Param("semesterEnrollmentId") Long semesterEnrollmentId);

    /**
     * One semester enrollment, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Adding a course to it
     * and ending it both take this lock, so a course is never added to an enrollment that is ending at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enrollment from SemesterEnrollment enrollment where enrollment.id = :semesterEnrollmentId")
    Optional<SemesterEnrollment> findByIdForUpdate(@Param("semesterEnrollmentId") Long semesterEnrollmentId);

    /**
     * The semester and academic session of each semester enrollment in these statuses of the student behind one
     * account, in one query and without loading the enrollments.
     */
    @Query("""
            select semester.id as semesterId, semester.academicSession.id as academicSessionId
            from SemesterEnrollment enrollment
            join enrollment.semester semester
            where enrollment.studentProfile.userAccount.id = :userAccountId
              and enrollment.status in :statuses
            """)
    List<EnrolledSemester> findEnrolledSemestersByUserAccountId(
            @Param("userAccountId") Long userAccountId, @Param("statuses") Collection<SemesterEnrollmentStatus> statuses);

    /**
     * The academic sessions in which a student has at least one semester enrollment in this status, the latest first.
     * Hostel eligibility uses it with ENROLLED.
     */
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

    /** Whether a student has a semester enrollment in this status in a semester of the academic session. */
    @Query("""
            select count(enrollment) > 0
            from SemesterEnrollment enrollment
            where enrollment.studentProfile.id = :studentProfileId
              and enrollment.semester.academicSession.id = :academicSessionId
              and enrollment.status = :status
            """)
    boolean existsInSessionWithStatus(@Param("studentProfileId") Long studentProfileId,
            @Param("academicSessionId") Long academicSessionId, @Param("status") SemesterEnrollmentStatus status);

    /** One semester a student is enrolled in, and its academic session. */
    interface EnrolledSemester {

        Long getSemesterId();

        Long getAcademicSessionId();
    }

    /**
     * The accounts of the students with a semester enrollment in these statuses in one academic session, and, when a
     * semester is given, in that semester only. Each account once.
     */
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

    /** Reports: how many semester enrollments are in each status, in the semesters that match every given filter. */
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
}
