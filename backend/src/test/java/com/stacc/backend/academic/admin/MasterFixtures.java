package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up academic masters for the admin tests, with IDs set the way the database would. */
final class MasterFixtures {

    private MasterFixtures() {
    }

    static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static Department department(long id, String code, String name) {
        return withId(new Department(code, name), id);
    }

    static Program program(long id, Department department, String code, int duration) {
        return withId(new Program(department, code, "Program " + code, duration), id);
    }

    static AcademicSession session(long id, String code, int startYear) {
        return withId(new AcademicSession(code, LocalDate.of(startYear, 7, 1), LocalDate.of(startYear + 1, 6, 30)), id);
    }

    static Semester semester(long id, Program program, AcademicSession session, int number) {
        return withId(new Semester(program, session, number), id);
    }

    static Course course(long id, Department department, String code, String credits) {
        return withId(new Course(department, code, "Course " + code, new BigDecimal(credits)), id);
    }

    /** Answers a save the way the database would: a new record gets the next ID. */
    static <T> T saved(T entity, long id) {
        if (ReflectionTestUtils.getField(entity, "id") == null) {
            ReflectionTestUtils.setField(entity, "id", id);
        }
        return entity;
    }
}
