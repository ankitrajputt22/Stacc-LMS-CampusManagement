package com.stacc.backend.academic.calendar;

import java.time.LocalDate;
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

public interface AcademicCalendarEventRepository extends JpaRepository<AcademicCalendarEvent, Long> {

    @Query("""
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where event.id = :eventId
            """)
    Optional<AcademicCalendarEvent> findWithContextById(@Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AcademicCalendarEvent event where event.id = :eventId")
    Optional<AcademicCalendarEvent> findByIdForUpdate(@Param("eventId") Long eventId);

    @Query("""
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where (:academicSessionId is null or academicSession.id = :academicSessionId)
              and (:semesterId is null or semester.id = :semesterId)
              and (:status is null or event.status = :status)
              and (:from is null or event.endDate >= :from)
              and (:to is null or event.startDate <= :to)
            order by event.startDate asc, event.id asc
            """)
    List<AcademicCalendarEvent> findAllWithContext(
            @Param("academicSessionId") Long academicSessionId,
            @Param("semesterId") Long semesterId,
            @Param("status") AcademicCalendarEventStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and event.startDate <= :to
              and event.endDate >= :from
            order by event.startDate asc, event.id asc
            """)
    List<AcademicCalendarEvent> findVisibleInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and event.startDate <= :to
              and event.endDate >= :from
              and ((semester is null and academicSession.id in :academicSessionIds) or semester.id in :semesterIds)
            order by event.startDate asc, event.id asc
            """)
    List<AcademicCalendarEvent> findVisibleInRangeForScope(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("semesterIds") Collection<Long> semesterIds,
            @Param("academicSessionIds") Collection<Long> academicSessionIds);

    @Query(value = """
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and (lower(event.title) like :contains escape '!'
                   or lower(coalesce(event.description, '')) like :contains escape '!')
            order by case when lower(event.title) = :exact then 0 when lower(event.title) like :prefix escape '!' then 1 else 2 end, event.startDate asc, event.id asc
            """,
            countQuery = """
            select count(event)
            from AcademicCalendarEvent event
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and (lower(event.title) like :contains escape '!'
                   or lower(coalesce(event.description, '')) like :contains escape '!')
            """)
    Page<AcademicCalendarEvent> searchVisible(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);

    @Query(value = """
            select event
            from AcademicCalendarEvent event
            join fetch event.academicSession academicSession
            left join fetch event.semester semester
            left join fetch semester.program
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and ((semester is null and academicSession.id in :academicSessionIds) or semester.id in :semesterIds)
              and (lower(event.title) like :contains escape '!'
                   or lower(coalesce(event.description, '')) like :contains escape '!')
            order by case when lower(event.title) = :exact then 0 when lower(event.title) like :prefix escape '!' then 1 else 2 end, event.startDate asc, event.id asc
            """,
            countQuery = """
            select count(event)
            from AcademicCalendarEvent event
            join event.academicSession academicSession
            left join event.semester semester
            where (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.PUBLISHED
                   or (event.status = com.stacc.backend.academic.calendar.AcademicCalendarEventStatus.CANCELLED
                       and event.publishedAt is not null))
              and ((semester is null and academicSession.id in :academicSessionIds) or semester.id in :semesterIds)
              and (lower(event.title) like :contains escape '!'
                   or lower(coalesce(event.description, '')) like :contains escape '!')
            """)
    Page<AcademicCalendarEvent> searchVisibleForScope(@Param("semesterIds") Collection<Long> semesterIds,
            @Param("academicSessionIds") Collection<Long> academicSessionIds, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);
}
