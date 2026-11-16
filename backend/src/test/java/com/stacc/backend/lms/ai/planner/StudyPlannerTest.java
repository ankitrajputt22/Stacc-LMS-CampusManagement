package com.stacc.backend.lms.ai.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The planner: deadlines first, within each day's minutes, half on busy days, and pages spread by need. */
class StudyPlannerTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-11-16");

    @Test
    void assignmentWorkComesBeforeItsDeadlineAndDaysStayWithinBudget() {
        List<StudyPlanner.Day> plan = StudyPlanner.plan(MONDAY, 7, 60, List.of(
                new StudyPlanner.Need(StudyPlanner.Kind.ASSIGNMENT, 9L, "Lab 2", MONDAY.plusDays(3)),
                new StudyPlanner.Need(StudyPlanner.Kind.PRACTISE_WEAK, 2L, "Indexes", null),
                new StudyPlanner.Need(StudyPlanner.Kind.START_PAGE, 3L, "Views", null)), Set.of(MONDAY.plusDays(1)), true);

        assertThat(plan).hasSize(7);
        assertThat(plan).allMatch(day -> day.planned() <= day.budget());
        assertThat(plan.get(1).busy()).isTrue();
        assertThat(plan.get(1).budget()).isEqualTo(30);
        List<LocalDate> labDays = plan.stream().filter(day -> day.sessions().stream().anyMatch(session -> session.title().equals("Work on Lab 2")))
                .map(StudyPlanner.Day::date).toList();
        assertThat(labDays).hasSize(2).allMatch(date -> date.isBefore(MONDAY.plusDays(3)));
        assertThat(plan).allMatch(day -> day.sessions().stream().anyMatch(session -> session.kind() == StudyPlanner.Kind.FLASHCARDS));
        assertThat(plan.stream().flatMap(day -> day.sessions().stream()).map(StudyPlanner.Session::title))
                .contains("Practise Indexes", "Start Views");
    }

    @Test
    void nothingIsPlannedWithoutRoom() {
        List<StudyPlanner.Day> plan = StudyPlanner.plan(MONDAY, 7, 30, List.of(
                new StudyPlanner.Need(StudyPlanner.Kind.ASSIGNMENT, 9L, "Essay", MONDAY.plusDays(2))), Set.of(), false);
        assertThat(plan.stream().flatMap(day -> day.sessions().stream())).isEmpty();
    }
}
