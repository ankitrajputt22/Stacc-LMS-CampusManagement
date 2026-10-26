package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Academic master data for admins, under the rules of Academic Admin: departments, programs, sessions, semesters,
 * and catalog courses need ADMIN with ACADEMIC_MASTER_VIEW; course offerings ADMIN with ACADEMIC_OFFERING_VIEW. The
 * role alone is no pass. Records of every status are found, as the admin lists show them. No account, student, or
 * faculty record is ever searched.
 */
@Component
public class AcademicSearchProvider implements SearchProvider {

    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicSessionRepository sessions;
    private final SemesterRepository semesters;
    private final CourseRepository courses;
    private final CourseOfferingRepository offerings;

    public AcademicSearchProvider(DepartmentRepository departments, ProgramRepository programs,
            AcademicSessionRepository sessions, SemesterRepository semesters, CourseRepository courses,
            CourseOfferingRepository offerings) {
        this.departments = departments;
        this.programs = programs;
        this.sessions = sessions;
        this.semesters = semesters;
        this.courses = courses;
        this.offerings = offerings;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.DEPARTMENT, SearchResultType.PROGRAM, SearchResultType.ACADEMIC_SESSION,
                SearchResultType.SEMESTER, SearchResultType.CATALOG_COURSE, SearchResultType.COURSE_OFFERING);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        SearchText text = context.text();
        if (type == SearchResultType.COURSE_OFFERING) {
            return actor.holdsWith("ADMIN", "ACADEMIC_OFFERING_VIEW")
                    ? Optional.of(offerings.search(text.exact(), text.prefix(), text.contains(), page)
                            .map(AcademicSearchProvider::offeringOf))
                    : Optional.empty();
        }
        if (!actor.holdsWith("ADMIN", "ACADEMIC_MASTER_VIEW")) {
            return Optional.empty();
        }
        return switch (type) {
            case DEPARTMENT -> Optional.of(departments.search(text.exact(), text.prefix(), text.contains(), page)
                    .map(department -> new SearchResult(type, department.getName(),
                            SearchFormat.join(department.getCode(), SearchFormat.words(department.getStatus())), null,
                            "/admin/academic/departments", null)));
            case PROGRAM -> Optional.of(programs.search(text.exact(), text.prefix(), text.contains(), page)
                    .map(program -> new SearchResult(type, program.getName(),
                            SearchFormat.join(program.getCode(), program.getDepartment().getCode(),
                                    program.getDurationSemesters() + " semesters", SearchFormat.words(program.getStatus())),
                            null, "/admin/academic/programs", null)));
            case ACADEMIC_SESSION -> Optional.of(sessions.search(text.exact(), text.prefix(), text.contains(), page)
                    .map(session -> new SearchResult(type, session.getCode(),
                            SearchFormat.join(SearchFormat.days(session.getStartDate(), session.getEndDate()),
                                    SearchFormat.words(session.getStatus())),
                            null, "/admin/academic/sessions", null)));
            case SEMESTER -> Optional.of(semesters.search(text.number(), text.exact(), text.prefix(), text.contains(), page)
                    .map(AcademicSearchProvider::semesterOf));
            case CATALOG_COURSE -> Optional.of(courses.search(text.exact(), text.prefix(), text.contains(), page)
                    .map(course -> new SearchResult(type, course.getName(),
                            SearchFormat.join(course.getCode(), course.getDepartment().getCode(),
                                    course.getCredits().toPlainString() + " credits", SearchFormat.words(course.getStatus())),
                            null, "/admin/academic/courses", null)));
            default -> Optional.empty();
        };
    }

    private static SearchResult semesterOf(Semester semester) {
        return new SearchResult(SearchResultType.SEMESTER,
                semester.getProgram().getCode() + " Semester " + semester.getSemesterNumber(),
                SearchFormat.join(semester.getProgram().getName(), semester.getAcademicSession().getCode(),
                        SearchFormat.words(semester.getStatus())),
                null, "/admin/academic/semesters", null);
    }

    private static SearchResult offeringOf(CourseOffering offering) {
        Semester semester = offering.getSemester();
        return new SearchResult(SearchResultType.COURSE_OFFERING, offering.getCourse().getName(),
                SearchFormat.join(offering.getCourse().getCode(), semester.getProgram().getCode(),
                        "Semester " + semester.getSemesterNumber(), semester.getAcademicSession().getCode(),
                        SearchFormat.words(offering.getStatus())),
                null, "/admin/academic/offerings", null);
    }
}
