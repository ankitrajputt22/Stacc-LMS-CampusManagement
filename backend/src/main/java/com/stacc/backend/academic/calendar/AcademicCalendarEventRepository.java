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

/**
 * Calendar events, always with their session and, when they have one, their semester and its program, so a list is
 * one query. An event is in a date range when it starts on or before the range's last day and ends on or after its
 * first day, which includes events that run across either edge.
 */
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

    /**
     * One event, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Every change takes this lock, so
     * an edit and a cancellation of the same event never overwrite each other. Only the event's own row is locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AcademicCalendarEvent event where event.id = :eventId")
    Optional<AcademicCalendarEvent> findByIdForUpdate(@Param("eventId") Long eventId);

    /**
     * Every event, whatever its status, narrowed by each filter that is not null: the earliest start first. A
     * semester filter matches events of exactly that semester, not the session-wide events around it.
     */
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

    /**
     * Every event users may see in the date range, for any session or semester: published ones, and cancelled ones
     * that were published first ({@link AcademicCalendarEvent#isVisibleToUsers()}). The earliest start first.
     */
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

    /**
     * The events users may see in the date range that apply to these semesters or sessions: an event of one of the
     * semesters, or a session-wide event of one of the sessions. Neither collection may be empty. The earliest start
     * first.
     */
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

    /**
     * Search: every event users may see ({@link AcademicCalendarEvent#isVisibleToUsers()}) whose title or description
     * contains the text, for the admin calendar. Exact titles first, then titles that start with the text, then the
     * rest; within each, the earliest start first. Drafts never match.
     */
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

    /**
     * Search: the events users may see that apply to these semesters or sessions (an event of one of the semesters, or
     * a session-wide event of one of the sessions, as {@link #findVisibleInRangeForScope} decides), whose title or
     * description contains the text. Neither collection may be empty.
     */
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
