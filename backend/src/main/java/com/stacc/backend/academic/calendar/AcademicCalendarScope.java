package com.stacc.backend.academic.calendar;

import java.util.HashSet;
import java.util.Set;

public record AcademicCalendarScope(Set<Long> semesterIds, Set<Long> academicSessionIds) {

    public static final AcademicCalendarScope NONE = new AcademicCalendarScope(Set.of(), Set.of());

    public AcademicCalendarScope {
        semesterIds = Set.copyOf(semesterIds);
        academicSessionIds = Set.copyOf(academicSessionIds);
    }

    public boolean isEmpty() {
        return semesterIds.isEmpty() && academicSessionIds.isEmpty();
    }

    public AcademicCalendarScope and(AcademicCalendarScope other) {
        Set<Long> semesters = new HashSet<>(semesterIds);
        semesters.addAll(other.semesterIds);
        Set<Long> sessions = new HashSet<>(academicSessionIds);
        sessions.addAll(other.academicSessionIds);
        return new AcademicCalendarScope(semesters, sessions);
    }
}
