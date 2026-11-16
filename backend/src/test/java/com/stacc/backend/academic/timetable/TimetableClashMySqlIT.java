package com.stacc.backend.academic.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/** Two classes added at once into the same room and hour: the session row lock lets only one in. A room of its own. */
class TimetableClashMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private TimetableService timetable;

    @Autowired
    private CourseOfferingRepository offerings;

    @Test
    void aRoomIsNeverBookedTwiceAtOnce() throws Exception {
        List<CourseOffering> open = offerings.findAllWithContext(null, null, null, null, Pageable.unpaged()).stream()
                .filter(offering -> offering.getStatus() != CourseOfferingStatus.CLOSED)
                .toList();
        CourseOffering first = open.getFirst();
        CourseOffering second = open.stream()
                .filter(offering -> offering.getSemester().getAcademicSession().getId().equals(first.getSemester().getAcademicSession().getId()))
                .filter(offering -> !offering.getId().equals(first.getId()))
                .findFirst().orElseThrow();

        List<Concurrently.Outcome<TimetableService.SlotView>> outcomes = Concurrently.run(
                () -> timetable.add(first.getId(), DayOfWeek.SUNDAY, LocalTime.of(20, 0), LocalTime.of(21, 0), "RACE-ROOM", ClassTimetableSlot.Kind.LECTURE),
                () -> timetable.add(second.getId(), DayOfWeek.SUNDAY, LocalTime.of(20, 30), LocalTime.of(21, 30), "RACE-ROOM", ClassTimetableSlot.Kind.LECTURE));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
    }
}
