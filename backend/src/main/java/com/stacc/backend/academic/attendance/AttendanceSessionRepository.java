package com.stacc.backend.academic.attendance;

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

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    /** A course offering's sessions, the latest class first. */
    List<AttendanceSession> findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(Long courseOfferingId);

    /** One session with its course offering and course, for showing it. */
    @Query("""
            select session
            from AttendanceSession session
            join fetch session.courseOffering courseOffering
            join fetch courseOffering.course
            where session.id = :sessionId
            """)
    Optional<AttendanceSession> findWithCourseById(@Param("sessionId") Long sessionId);

    /**
     * One session, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Every change to a session
     * or its records takes this lock first, so a save and a finalization of the same session never overlap.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AttendanceSession session where session.id = :sessionId")
    Optional<AttendanceSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    /**
     * The open sessions of these course offerings, the latest class first, then the highest ID, one page of them with
     * the total count. Finalized sessions are never included.
     */
    @Query(value = """
            select session
            from AttendanceSession session
            where session.courseOffering.id in :courseOfferingIds
              and session.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.OPEN
            order by session.heldAt desc, session.id desc
            """,
            countQuery = """
            select count(session)
            from AttendanceSession session
            where session.courseOffering.id in :courseOfferingIds
              and session.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.OPEN
            """)
    Page<AttendanceSession> findOpenByCourseOfferingIdIn(
            @Param("courseOfferingIds") Collection<Long> courseOfferingIds, Pageable page);
}
