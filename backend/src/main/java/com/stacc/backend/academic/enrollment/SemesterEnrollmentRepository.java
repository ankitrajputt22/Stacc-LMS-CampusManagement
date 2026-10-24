package com.stacc.backend.academic.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
