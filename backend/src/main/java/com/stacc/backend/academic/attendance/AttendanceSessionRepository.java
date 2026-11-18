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

    List<AttendanceSession> findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(Long courseOfferingId);

    @Query("""
            select session
            from AttendanceSession session
            join fetch session.courseOffering courseOffering
            join fetch courseOffering.course
            where session.id = :sessionId
            """)
    Optional<AttendanceSession> findWithCourseById(@Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AttendanceSession session where session.id = :sessionId")
    Optional<AttendanceSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

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

    @Query("""
            select session.courseOffering.id as courseOfferingId, session.status as status, count(session) as total
            from AttendanceSession session
            where session.courseOffering.id in :courseOfferingIds
            group by session.courseOffering.id, session.status
            """)
    List<OfferingStatusCount> countByOfferingAndStatus(@Param("courseOfferingIds") Collection<Long> courseOfferingIds);

    interface OfferingStatusCount {
        Long getCourseOfferingId();

        AttendanceSessionStatus getStatus();

        Long getTotal();
    }
}
