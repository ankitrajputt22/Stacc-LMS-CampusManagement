package com.stacc.backend.academic.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

/** Two seat plans of one exam at once: the exam row lock lets them take turns, so no seat is given twice. An exam of its own. */
class ExamSeatPlanMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private ExamService exams;

    @Autowired
    private CourseOfferingRepository offerings;

    @Test
    void seatsAreGivenOnce() throws Exception {
        CourseOffering offering = offerings.findAllWithContext(null, null, null, null, Pageable.unpaged()).stream()
                .filter(found -> found.getStatus() != CourseOfferingStatus.CLOSED)
                .filter(found -> found.getSemester().getStatus() != com.stacc.backend.academic.semester.SemesterStatus.CLOSED)
                .findFirst().orElseThrow();
        Long sessionId = exams.createSession(offering.getSemester().getId(), "Race session").examSessionId();
        Long slotId = exams.addExam(sessionId, offering.getId(), LocalDate.of(2027, 1, 4), LocalTime.of(10, 0), LocalTime.of(13, 0),
                "RACE-HALL", 500).slotId();

        List<Concurrently.Outcome<ExamService.SeatPlan>> outcomes = Concurrently.run(() -> exams.seatPlan(slotId), () -> exams.seatPlan(slotId));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        int added = outcomes.stream().mapToInt(outcome -> outcome.result().added()).sum();
        ExamService.SessionView session = exams.forSemester(offering.getSemester().getId()).stream()
                .filter(view -> view.examSessionId().equals(sessionId)).findFirst().orElseThrow();
        assertThat(session.exams()).singleElement().satisfies(slot -> {
            assertThat(slot.seatsGiven()).isEqualTo(slot.studentsEnrolled());
            assertThat(added).isEqualTo((int) slot.studentsEnrolled());
        });
    }
}
