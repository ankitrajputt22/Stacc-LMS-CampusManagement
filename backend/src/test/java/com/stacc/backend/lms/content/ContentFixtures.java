package com.stacc.backend.lms.content;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.lms.course.LmsCourse;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up course content for tests, with IDs set the way the database would set them. */
public final class ContentFixtures {

    private ContentFixtures() {
    }

    public static LmsCourse lmsCourse(long id) {
        Department department = new Department("CSE", "Computer Science and Engineering");
        Program program = new Program(department, "BTECH", "Bachelor of Technology", 8);
        AcademicSession session = new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
        Course course = new Course(department, "TST301", "Test Data Structures", new BigDecimal("4.00"));
        CourseOffering offering = new CourseOffering(course, new Semester(program, session, 3));
        ReflectionTestUtils.setField(offering, "id", id + 100);
        LmsCourse lmsCourse = new LmsCourse(offering);
        ReflectionTestUtils.setField(lmsCourse, "id", id);
        return lmsCourse;
    }

    public static LmsModule module(LmsCourse lmsCourse, long id, int position, LmsContentStatus status) {
        LmsModule module = new LmsModule(lmsCourse, "Module " + id, null, position);
        ReflectionTestUtils.setField(module, "id", id);
        ReflectionTestUtils.setField(module, "status", status);
        return module;
    }

    public static LmsStudyMaterial material(LmsTopic topic, long id, int position, LmsContentStatus status) {
        LmsStudyMaterial material = new LmsStudyMaterial(topic, "Material " + id, null, "lms/test/key-" + id,
                "material-" + id + ".pdf", "application/pdf", 1000 + id, position);
        ReflectionTestUtils.setField(material, "id", id);
        ReflectionTestUtils.setField(material, "status", status);
        return material;
    }

    public static LmsTopic topic(LmsModule module, long id, int position, LmsContentStatus status) {
        LmsTopic topic = new LmsTopic(module, "Topic " + id, "Text of topic " + id, position);
        ReflectionTestUtils.setField(topic, "id", id);
        ReflectionTestUtils.setField(topic, "status", status);
        return topic;
    }
}
