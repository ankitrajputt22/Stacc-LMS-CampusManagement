package com.stacc.backend.academic.attendance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** A session's records, each with the student's account, in student ID order. */
    @Query("""
            select record
            from AttendanceRecord record
            join fetch record.courseEnrollment courseEnrollment
            join fetch courseEnrollment.semesterEnrollment semesterEnrollment
            join fetch semesterEnrollment.studentProfile studentProfile
            join fetch studentProfile.userAccount userAccount
            where record.attendanceSession.id = :sessionId
            order by userAccount.loginId asc, record.id asc
            """)
    List<AttendanceRecord> findAllWithStudentBySessionId(@Param("sessionId") Long sessionId);

    long countByAttendanceSessionIdAndStatus(Long attendanceSessionId, AttendanceStatus status);

    /**
     * The finalized attendance of the student behind one account, in one query, with each session and its
     * course offering, course, semester, programme, and academic session. Open sessions are never included.
     * The latest class comes first.
     */
    @Query("""
            select record
            from AttendanceRecord record
            join fetch record.attendanceSession session
            join fetch session.courseOffering courseOffering
            join fetch courseOffering.course
            join fetch courseOffering.semester semester
            join fetch semester.program
            join fetch semester.academicSession
            where record.courseEnrollment.semesterEnrollment.studentProfile.userAccount.id = :userAccountId
              and session.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.FINALIZED
            order by session.heldAt desc, session.id desc
            """)
    List<AttendanceRecord> findAllFinalizedByUserAccountId(@Param("userAccountId") Long userAccountId);
}
