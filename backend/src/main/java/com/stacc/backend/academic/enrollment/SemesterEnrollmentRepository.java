package com.stacc.backend.academic.enrollment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterEnrollmentRepository extends JpaRepository<SemesterEnrollment, Long> {

    Optional<SemesterEnrollment> findByStudentProfileIdAndSemesterId(Long studentProfileId, Long semesterId);

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
}
