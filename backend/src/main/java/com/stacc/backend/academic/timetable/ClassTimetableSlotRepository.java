package com.stacc.backend.academic.timetable;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassTimetableSlotRepository extends JpaRepository<ClassTimetableSlot, Long> {

    @Query("""
            select slot from ClassTimetableSlot slot
            join fetch slot.courseOffering offering
            join fetch offering.course
            join fetch offering.semester semester
            left join fetch offering.section
            where offering.id in :offeringIds and slot.removedAt is null
            order by slot.dayOfWeek, slot.startsAt, slot.id
            """)
    List<ClassTimetableSlot> findActiveForOfferings(@Param("offeringIds") Collection<Long> offeringIds);

    @Query("""
            select slot from ClassTimetableSlot slot
            join fetch slot.courseOffering offering
            join fetch offering.course
            left join fetch offering.section
            where offering.semester.id = :semesterId and slot.removedAt is null
            order by slot.dayOfWeek, slot.startsAt, slot.id
            """)
    List<ClassTimetableSlot> findActiveForSemester(@Param("semesterId") Long semesterId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select slot from ClassTimetableSlot slot
            join fetch slot.courseOffering offering
            join fetch offering.course
            join fetch offering.semester
            left join fetch offering.section
            where offering.semester.academicSession.id = :sessionId and slot.dayOfWeek = :day and slot.removedAt is null
            """)
    List<ClassTimetableSlot> findActiveForSessionOnDay(@Param("sessionId") Long sessionId, @Param("day") int day);
}
