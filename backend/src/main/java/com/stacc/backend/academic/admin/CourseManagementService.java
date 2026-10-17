package com.stacc.backend.academic.admin;

import java.util.List;
import java.util.Locale;

import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.CourseUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.CourseResponse;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit the permanent course catalog. A course has one owning department, loaded on the
 * server from the ID sent; its department and code are set once, and its name, credits, and status can change. A
 * course has no semester, session, teacher, or LMS course: those belong to course offerings. Nothing is ever deleted.
 */
@Service
@Transactional
public class CourseManagementService {

    static final String NOT_FOUND = "Course not found.";
    static final String DEPARTMENT_MISSING = "The selected department does not exist.";
    static final String CODE_TAKEN = "A course with this code already exists.";

    private final CourseRepository courses;
    private final DepartmentRepository departments;

    public CourseManagementService(CourseRepository courses, DepartmentRepository departments) {
        this.courses = courses;
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list() {
        return courses.findAllWithDepartment().stream().map(CourseResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long courseId) {
        return CourseResponse.of(find(courseId));
    }

    public CourseResponse create(CourseCreate request) {
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> AcademicMasterErrors.badRequest(DEPARTMENT_MISSING));
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (courses.existsByCode(code)) {
            throw AcademicMasterErrors.conflict(CODE_TAKEN);
        }
        return CourseResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> courses.saveAndFlush(new Course(department, code, request.name().strip(), request.credits())), CODE_TAKEN));
    }

    public CourseResponse update(Long courseId, CourseUpdate request) {
        Course course = find(courseId);
        course.rename(request.name().strip());
        course.changeCredits(request.credits());
        if (request.status() == CourseStatus.ACTIVE) {
            course.activate();
        } else {
            course.deactivate();
        }
        return CourseResponse.of(courses.saveAndFlush(course));
    }

    private Course find(Long courseId) {
        return courses.findWithDepartmentById(courseId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
