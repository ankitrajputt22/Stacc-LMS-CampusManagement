package com.stacc.backend.dashboard;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.dashboard.AdminDashboardResponse.AcademicMasters;
import com.stacc.backend.dashboard.AdminDashboardResponse.AcademicOperations;
import com.stacc.backend.dashboard.AdminDashboardResponse.CourseOfferingPreview;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the admin dashboard with count queries and one bounded preview query, at request time; nothing is stored.
 * Master and offering counts are every row, whatever its status. "Active" enrollment means {@code ENROLLED} only. The
 * offering preview reuses the admin offering list's own query and order, limited to its first page.
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardQueryService {

    static final int OFFERING_PREVIEW_LIMIT = 5;

    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicSessionRepository academicSessions;
    private final SemesterRepository semesters;
    private final CourseRepository courses;
    private final CourseOfferingRepository courseOfferings;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;

    public AdminDashboardQueryService(DepartmentRepository departments, ProgramRepository programs,
            AcademicSessionRepository academicSessions, SemesterRepository semesters, CourseRepository courses,
            CourseOfferingRepository courseOfferings, SemesterEnrollmentRepository semesterEnrollments,
            CourseEnrollmentRepository courseEnrollments) {
        this.departments = departments;
        this.programs = programs;
        this.academicSessions = academicSessions;
        this.semesters = semesters;
        this.courses = courses;
        this.courseOfferings = courseOfferings;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
    }

    /** The dashboard as it is now. Empty data gives zero counts and an empty preview, never an error. */
    public AdminDashboardResponse getDashboard() {
        AcademicMasters masters = new AcademicMasters(departments.count(), programs.count(), academicSessions.count(),
                semesters.count(), courses.count());
        AcademicOperations operations = new AcademicOperations(courseOfferings.count(),
                semesterEnrollments.countByStatus(SemesterEnrollmentStatus.ENROLLED),
                courseEnrollments.countByStatus(CourseEnrollmentStatus.ENROLLED));
        // No filter, first page only: one query with the course, semester, program, and session joined in.
        var preview = courseOfferings.findAllWithContext(null, null, null, null, PageRequest.of(0, OFFERING_PREVIEW_LIMIT))
                .stream()
                .map(AdminDashboardQueryService::previewOf)
                .toList();
        return new AdminDashboardResponse(masters, operations, preview);
    }

    private static CourseOfferingPreview previewOf(CourseOffering offering) {
        Course course = offering.getCourse();
        Semester semester = offering.getSemester();
        return new CourseOfferingPreview(offering.getId(), course.getCode(), course.getName(), course.getCredits(),
                semester.getSemesterNumber(), semester.getProgram().getCode(), semester.getAcademicSession().getCode());
    }
}
