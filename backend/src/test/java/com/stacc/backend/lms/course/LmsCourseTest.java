package com.stacc.backend.lms.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import org.junit.jupiter.api.Test;

class LmsCourseTest {

    private final Department department = new Department("CSE", "Computer Science and Engineering");
    private final Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final Course course = new Course(department, "BCS301", "Data Structures", new BigDecimal("4.00"));
    private final CourseOffering offering = new CourseOffering(course, semester);

    @Test
    void newLmsCourseIsADraftAndLinksItsCourseOffering() {
        LmsCourse lmsCourse = new LmsCourse(offering);

        assertSame(offering, lmsCourse.getCourseOffering());
        assertEquals(LmsCourseStatus.DRAFT, lmsCourse.getStatus());
    }

    @Test
    void courseOfferingIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsCourse(null));
    }

    @Test
    void academicContextIsReachedThroughTheCourseOffering() {
        LmsCourse lmsCourse = new LmsCourse(offering);

        assertSame(course, lmsCourse.getCourseOffering().getCourse());
        assertSame(semester, lmsCourse.getCourseOffering().getSemester());
        assertSame(program, lmsCourse.getCourseOffering().getSemester().getProgram());
        assertSame(session, lmsCourse.getCourseOffering().getSemester().getAcademicSession());
        assertSame(department, lmsCourse.getCourseOffering().getCourse().getDepartment());
        assertEquals("BCS301", lmsCourse.getCourseOffering().getCourse().getCode());
    }

    @Test
    void courseOfferingIsTheOnlyAcademicDataStoredOnTheLmsCourse() {
        List<String> fields = Arrays.stream(LmsCourse.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertEquals(List.of("id", "courseOffering", "status", "createdAt", "updatedAt"), fields);
    }

    @Test
    void draftCanBePublished() {
        LmsCourse lmsCourse = new LmsCourse(offering);

        lmsCourse.publish();

        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }

    @Test
    void draftOrPublishedLmsCourseCanBeArchived() {
        LmsCourse draft = new LmsCourse(offering);
        LmsCourse published = new LmsCourse(offering);
        published.publish();

        draft.archive();
        published.archive();

        assertEquals(LmsCourseStatus.ARCHIVED, draft.getStatus());
        assertEquals(LmsCourseStatus.ARCHIVED, published.getStatus());
    }

    @Test
    void archivedLmsCourseCannotBePublishedAgain() {
        LmsCourse lmsCourse = new LmsCourse(offering);
        lmsCourse.archive();

        assertThrows(IllegalStateException.class, lmsCourse::publish);
        assertEquals(LmsCourseStatus.ARCHIVED, lmsCourse.getStatus());
    }

    @Test
    void lmsCourseStatusAndOfferingStatusAreIndependent() {
        CourseOffering closed = new CourseOffering(course, semester);
        closed.close();

        LmsCourse forPlannedOffering = new LmsCourse(offering);
        LmsCourse forClosedOffering = new LmsCourse(closed);
        forPlannedOffering.publish();

        assertEquals(CourseOfferingStatus.PLANNED, offering.getStatus());
        assertEquals(LmsCourseStatus.DRAFT, forClosedOffering.getStatus());
        assertEquals(CourseOfferingStatus.CLOSED, closed.getStatus());
    }
}
