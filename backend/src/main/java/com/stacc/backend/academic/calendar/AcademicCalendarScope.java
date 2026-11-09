package com.stacc.backend.academic.calendar;

import java.util.HashSet;
import java.util.Set;

/**
 * The semesters and academic sessions whose calendar events apply to someone: the events of each semester, and the
 * session-wide events of each session. It only names IDs found on the server from the signed-in account.
 */
public record AcademicCalendarScope(Set<Long> semesterIds, Set<Long> academicSessionIds) {

    public static final AcademicCalendarScope NONE = new AcademicCalendarScope(Set.of(), Set.of());

    public AcademicCalendarScope {
        semesterIds = Set.copyOf(semesterIds);
        academicSessionIds = Set.copyOf(academicSessionIds);
    }

    public boolean isEmpty() {
        return semesterIds.isEmpty() && academicSessionIds.isEmpty();
    }

    /** Both scopes together, for an account that is, for example, a student and a faculty member. */
    public AcademicCalendarScope and(AcademicCalendarScope other) {
        Set<Long> semesters = new HashSet<>(semesterIds);
        semesters.addAll(other.semesterIds);
        Set<Long> sessions = new HashSet<>(academicSessionIds);
        sessions.addAll(other.academicSessionIds);
        return new AcademicCalendarScope(semesters, sessions);
    }
}
