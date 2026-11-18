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

    public AdminDashboardResponse getDashboard() {
        AcademicMasters masters = new AcademicMasters(departments.count(), programs.count(), academicSessions.count(),
                semesters.count(), courses.count());
        AcademicOperations operations = new AcademicOperations(courseOfferings.count(),
                semesterEnrollments.countByStatus(SemesterEnrollmentStatus.ENROLLED),
                courseEnrollments.countByStatus(CourseEnrollmentStatus.ENROLLED));
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
