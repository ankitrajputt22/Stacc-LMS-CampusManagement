package com.stacc.backend.academic.attendance;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select record from AttendanceRecord record
            join fetch record.attendanceSession session
            join fetch record.courseEnrollment enrollment
            join fetch enrollment.courseOffering offering
            join fetch offering.course
            where record.id = :id
            """)
    java.util.Optional<AttendanceRecord> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);


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

    @Query("""
            select record.attendanceSession.id as sessionId,
                   count(record) as totalRecords,
                   sum(case when record.status = com.stacc.backend.academic.attendance.AttendanceStatus.UNMARKED
                            then 1 else 0 end) as unmarkedRecords
            from AttendanceRecord record
            where record.attendanceSession.id in :sessionIds
            group by record.attendanceSession.id
            """)
    List<SessionRecordCounts> countRecordsBySessionIdIn(@Param("sessionIds") Collection<Long> sessionIds);

    interface SessionRecordCounts {

        Long getSessionId();

        Long getTotalRecords();

        Long getUnmarkedRecords();
    }

    @Query("""
            select record.status as status, count(record) as total
            from AttendanceRecord record
            join record.attendanceSession session
            where session.courseOffering.id = :courseOfferingId
              and session.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.FINALIZED
            group by record.status
            """)
    List<RecordStatusCount> countFinalizedRecordsByStatus(@Param("courseOfferingId") Long courseOfferingId);

    interface RecordStatusCount {
        AttendanceStatus getStatus();

        Long getTotal();
    }
}
