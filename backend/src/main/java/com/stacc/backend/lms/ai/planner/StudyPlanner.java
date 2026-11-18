package com.stacc.backend.lms.ai.planner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StudyPlanner {

    static final int ASSIGNMENT_MINUTES = 45;
    static final int PRACTICE_MINUTES = 25;
    static final int REFRESH_MINUTES = 15;
    static final int FLASHCARD_MINUTES = 10;

    public enum Kind { ASSIGNMENT, PRACTISE_WEAK, START_PAGE, REFRESH_PAGE, FLASHCARDS }

    public record Need(Kind kind, Long targetId, String title, LocalDate dueOn) {
    }

    public record Session(Kind kind, Long targetId, String title, int minutes) {
    }

    public record Day(LocalDate date, boolean busy, int budget, List<Session> sessions) {

        public int planned() {
            return sessions.stream().mapToInt(Session::minutes).sum();
        }
    }

    private StudyPlanner() {
    }

    public static List<Day> plan(LocalDate start, int days, int minutesPerDay, List<Need> needs, Set<LocalDate> busyDays,
            boolean hasFlashcards) {
        List<Day> plan = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = start.plusDays(offset);
            boolean busy = busyDays.contains(date);
            plan.add(new Day(date, busy, busy ? minutesPerDay / 2 : minutesPerDay, new ArrayList<>()));
        }
        if (hasFlashcards) {
            plan.forEach(day -> place(day, new Session(Kind.FLASHCARDS, null, "Review flashcards", FLASHCARD_MINUTES)));
        }
        needs.stream().filter(need -> need.kind() == Kind.ASSIGNMENT && need.dueOn() != null).forEach(need -> {
            int placed = 0;
            for (int index = plan.size() - 1; index >= 0 && placed < 2; index--) {
                Day day = plan.get(index);
                if (day.date().isBefore(need.dueOn())
                        && place(day, new Session(Kind.ASSIGNMENT, need.targetId(), "Work on " + need.title(), ASSIGNMENT_MINUTES))) {
                    placed++;
                }
            }
        });
        for (Kind kind : List.of(Kind.PRACTISE_WEAK, Kind.START_PAGE, Kind.REFRESH_PAGE)) {
            int minutes = kind == Kind.REFRESH_PAGE ? REFRESH_MINUTES : PRACTICE_MINUTES;
            String verb = kind == Kind.PRACTISE_WEAK ? "Practise " : kind == Kind.START_PAGE ? "Start " : "Refresh ";
            for (Need need : needs) {
                if (need.kind() == kind) {
                    for (Day day : plan) {
                        if (place(day, new Session(kind, need.targetId(), verb + need.title(), minutes))) {
                            break;
                        }
                    }
                }
            }
        }
        return plan.stream().map(day -> new Day(day.date(), day.busy(), day.budget(), List.copyOf(day.sessions()))).toList();
    }

    private static boolean place(Day day, Session session) {
        if (day.planned() + session.minutes() > day.budget()) {
            return false;
        }
        day.sessions().add(session);
        return true;
    }
}
