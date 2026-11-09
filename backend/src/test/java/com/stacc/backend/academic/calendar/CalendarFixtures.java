package com.stacc.backend.academic.calendar;

import java.time.LocalDate;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A small made-up academic calendar setting: one program with semesters 3 and 4 in session 2026-27, and semester 5 in
 * session 2027-28. IDs are set the way the database would.
 */
public final class CalendarFixtures {

    public final Department cse = withId(new Department("CSE", "Computer Science and Engineering"), 3);
    public final Program btech = withId(new Program(cse, "BTECH-CSE", "Bachelor of Technology in CSE", 8), 4);
    public final AcademicSession session = withId(
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)), 2);
    public final AcademicSession nextSession = withId(
            new AcademicSession("2027-28", LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30)), 5);
    public final Semester third = withId(new Semester(btech, session, 3), 9);
    public final Semester fourth = withId(new Semester(btech, session, 4), 10);
    public final Semester nextYearFifth = withId(new Semester(btech, nextSession, 5), 12);

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** A draft event with an ID, as if it had been saved. */
    public AcademicCalendarEvent event(long id, AcademicSession session, Semester semester, String title, LocalDate start,
            LocalDate end) {
        return withId(new AcademicCalendarEvent(session, semester, title, null, AcademicCalendarEventCategory.ACADEMIC,
                start, end), id);
    }
}
