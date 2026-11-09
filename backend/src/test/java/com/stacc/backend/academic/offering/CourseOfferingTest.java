package com.stacc.backend.academic.offering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import org.junit.jupiter.api.Test;

class CourseOfferingTest {

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final Course course = new Course(department, "BCS301", "Data Structures", new BigDecimal("4.00"));

    @Test
    void newOfferingIsPlannedAndLinksItsCourseAndSemester() {
        CourseOffering offering = new CourseOffering(course, semester);

        assertSame(course, offering.getCourse());
        assertSame(semester, offering.getSemester());
        assertEquals(CourseOfferingStatus.PLANNED, offering.getStatus());
    }

    @Test
    void courseAndSemesterAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new CourseOffering(null, semester));
        assertThrows(IllegalArgumentException.class, () -> new CourseOffering(course, null));
    }

    @Test
    void programSessionAndDepartmentAreReachedThroughTheSemesterAndCourse() {
        CourseOffering offering = new CourseOffering(course, semester);

        assertSame(program, offering.getSemester().getProgram());
        assertSame(session, offering.getSemester().getAcademicSession());
        assertSame(department, offering.getCourse().getDepartment());
    }

    @Test
    void sameCourseCanBeOfferedInDifferentSemesters() {
        AcademicSession nextSession =
                new AcademicSession("2027-28", LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30));
        Semester nextYear = new Semester(program, nextSession, 3);

        CourseOffering first = new CourseOffering(course, semester);
        CourseOffering second = new CourseOffering(course, nextYear);

        assertSame(first.getCourse(), second.getCourse());
        assertSame(nextYear, second.getSemester());
    }

    @Test
    void offeringCanReferToACourseThatIsNoLongerActive() {
        course.deactivate();

        CourseOffering offering = new CourseOffering(course, semester);

        assertSame(course, offering.getCourse());
    }

    @Test
    void plannedOfferingBecomesActiveAndThenClosed() {
        CourseOffering offering = new CourseOffering(course, semester);

        offering.markActive();
        assertEquals(CourseOfferingStatus.ACTIVE, offering.getStatus());

        offering.close();
        assertEquals(CourseOfferingStatus.CLOSED, offering.getStatus());
    }

    @Test
    void closedOfferingCannotBeMadeActiveAgain() {
        CourseOffering offering = new CourseOffering(course, semester);
        offering.close();

        assertThrows(IllegalStateException.class, offering::markActive);
        assertEquals(CourseOfferingStatus.CLOSED, offering.getStatus());
    }
}
