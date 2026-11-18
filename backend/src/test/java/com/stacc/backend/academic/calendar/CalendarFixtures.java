package com.stacc.backend.academic.calendar;

import java.time.LocalDate;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import org.springframework.test.util.ReflectionTestUtils;

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

    public AcademicCalendarEvent event(long id, AcademicSession session, Semester semester, String title, LocalDate start,
            LocalDate end) {
        return withId(new AcademicCalendarEvent(session, semester, title, null, AcademicCalendarEventCategory.ACADEMIC,
                start, end), id);
    }
}
