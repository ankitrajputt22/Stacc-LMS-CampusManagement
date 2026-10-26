package com.stacc.backend.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsFacultyCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * LMS search, for students and for faculty, never mixed.
 *
 * <p>Students search only the courses {@link LmsStudentCourseQueryService} lists for them, the same rule as their
 * course list and every course page, and inside those only published assignments and announcements and materials whose
 * whole chain is published ({@link LmsContentPolicy#PUBLISHED_ONLY}). Faculty search the offerings they currently teach
 * ({@link LmsFacultyManagedCourseQueryService}, as the Teaching page) and, inside the draft or published LMS courses
 * {@link LmsFacultyCourseQueryService} lists for them, everything that is not archived ({@link LmsContentPolicy#IN_USE}).
 * Each side needs the role and permissions of its own routes. An account without accessible courses sends no query.
 */
@Component
public class LmsSearchProvider implements SearchProvider {

    static final String STUDENT_COURSES = "lms.studentCourses";
    static final String FACULTY_COURSES = "lms.facultyCourses";
    static final String MANAGED_COURSES = "lms.managedCourses";

    private final LmsStudentCourseQueryService studentCourses;
    private final LmsFacultyCourseQueryService facultyCourses;
    private final LmsFacultyManagedCourseQueryService managedCourses;
    private final LmsAssignmentRepository assignments;
    private final LmsAnnouncementRepository announcements;
    private final LmsStudyMaterialRepository materials;

    public LmsSearchProvider(LmsStudentCourseQueryService studentCourses, LmsFacultyCourseQueryService facultyCourses,
            LmsFacultyManagedCourseQueryService managedCourses, LmsAssignmentRepository assignments,
            LmsAnnouncementRepository announcements, LmsStudyMaterialRepository materials) {
        this.studentCourses = studentCourses;
        this.facultyCourses = facultyCourses;
        this.managedCourses = managedCourses;
        this.assignments = assignments;
        this.announcements = announcements;
        this.materials = materials;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.COURSE, SearchResultType.ASSIGNMENT, SearchResultType.STUDY_MATERIAL,
                SearchResultType.ANNOUNCEMENT, SearchResultType.TEACHING_COURSE, SearchResultType.TEACHING_ASSIGNMENT,
                SearchResultType.TEACHING_MATERIAL, SearchResultType.TEACHING_ANNOUNCEMENT);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        boolean student = actor.holdsWith("STUDENT", "LMS_COURSE_VIEW");
        boolean faculty = actor.holdsWith("FACULTY", "FACULTY_COURSE_VIEW");
        return switch (type) {
            case COURSE -> student ? Optional.of(studentCourses(context, page)) : Optional.empty();
            case ASSIGNMENT -> student ? Optional.of(studentAssignments(context, page)) : Optional.empty();
            case STUDY_MATERIAL -> student ? Optional.of(studentMaterials(context, page)) : Optional.empty();
            case ANNOUNCEMENT -> student ? Optional.of(studentAnnouncements(context, page)) : Optional.empty();
            // The Teaching page's list needs LMS_COURSE_MANAGE too; its results open that page.
            case TEACHING_COURSE -> actor.holdsWith("FACULTY", "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE")
                    ? Optional.of(teachingCourses(context, page)) : Optional.empty();
            case TEACHING_ASSIGNMENT -> faculty ? Optional.of(teachingAssignments(context, page)) : Optional.empty();
            case TEACHING_MATERIAL -> faculty ? Optional.of(teachingMaterials(context, page)) : Optional.empty();
            case TEACHING_ANNOUNCEMENT -> faculty ? Optional.of(teachingAnnouncements(context, page)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    // --- Students

    private Map<Long, LmsCourseSummaryResponse> studentCourseMap(SearchContext context) {
        return context.remember(STUDENT_COURSES, () -> studentCourses.getAccessibleCourses(context.actor().accountId())
                .stream()
                .collect(Collectors.toMap(LmsCourseSummaryResponse::lmsCourseId, Function.identity(), (a, b) -> a)));
    }

    /** The student's own course list is short; it is matched here, with the same ranking the queries use. */
    private Page<SearchResult> studentCourses(SearchContext context, Pageable page) {
        SearchText text = context.text();
        List<SearchResult> matches = studentCourseMap(context).values().stream()
                .filter(course -> text.matches(course.courseCode()) || text.matches(course.courseName())
                        || text.matches(course.programCode()) || text.matches(course.academicSessionCode()))
                .sorted(Comparator.<LmsCourseSummaryResponse>comparingInt(
                                course -> Math.min(text.rank(course.courseCode()), text.rank(course.courseName())))
                        .thenComparing(LmsCourseSummaryResponse::courseCode)
                        .thenComparing(LmsCourseSummaryResponse::lmsCourseId))
                .map(course -> new SearchResult(SearchResultType.COURSE, course.courseName(),
                        SearchFormat.join(course.courseCode(), course.programCode(), "Semester " + course.semesterNumber(),
                                course.academicSessionCode()),
                        null, "/courses/" + course.lmsCourseId(), null))
                .toList();
        return pageOf(matches, page);
    }

    private Page<SearchResult> studentAssignments(SearchContext context, Pageable page) {
        Map<Long, LmsCourseSummaryResponse> courses = studentCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return assignments.search(courses.keySet(), LmsContentPolicy.PUBLISHED_ONLY, text.exact(), text.prefix(),
                text.contains(), page).map(assignment -> {
                    LmsCourseSummaryResponse course = courses.get(courseIdOf(assignment));
                    return new SearchResult(SearchResultType.ASSIGNMENT, assignment.getTitle(),
                            courseLabel(course.courseCode(), course.courseName()), null,
                            "/courses/" + course.lmsCourseId() + "/assignments/" + assignment.getId(),
                            assignment.getDueAt());
                });
    }

    private Page<SearchResult> studentAnnouncements(SearchContext context, Pageable page) {
        Map<Long, LmsCourseSummaryResponse> courses = studentCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return announcements.search(courses.keySet(), LmsContentPolicy.PUBLISHED_ONLY, text.exact(), text.prefix(),
                text.contains(), page).map(announcement -> {
                    LmsCourseSummaryResponse course = courses.get(courseIdOf(announcement));
                    return new SearchResult(SearchResultType.ANNOUNCEMENT, announcement.getTitle(),
                            courseLabel(course.courseCode(), course.courseName()),
                            SearchFormat.snippet(announcement.getMessage()), "/courses/" + course.lmsCourseId(),
                            announcement.getPublishedAt());
                });
    }

    private Page<SearchResult> studentMaterials(SearchContext context, Pageable page) {
        Map<Long, LmsCourseSummaryResponse> courses = studentCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return materials.search(courses.keySet(), LmsContentPolicy.PUBLISHED_ONLY, text.exact(), text.prefix(),
                text.contains(), page).map(material -> {
                    // The query narrows to published chains; the policy is still the rule, so a mismatch is a bug.
                    if (!LmsContentPolicy.isVisibleToStudents(material)) {
                        throw new IllegalStateException("A study material outside the published chain was found");
                    }
                    LmsCourseSummaryResponse course = courses.get(courseIdOf(material));
                    return new SearchResult(SearchResultType.STUDY_MATERIAL, material.getTitle(),
                            SearchFormat.join(courseLabel(course.courseCode(), course.courseName()),
                                    material.getOriginalFileName()),
                            SearchFormat.snippet(material.getDescription()), "/courses/" + course.lmsCourseId(), null);
                });
    }

    // --- Faculty

    private Map<Long, LmsFacultyCourseSummaryResponse> facultyCourseMap(SearchContext context) {
        return context.remember(FACULTY_COURSES, () -> facultyCourses.getAccessibleCourses(context.actor().accountId())
                .stream()
                .collect(Collectors.toMap(LmsFacultyCourseSummaryResponse::lmsCourseId, Function.identity(), (a, b) -> a)));
    }

    /** Every offering taught, with or without an LMS course; it is matched here, ranked as the queries rank. */
    private Page<SearchResult> teachingCourses(SearchContext context, Pageable page) {
        SearchText text = context.text();
        List<LmsFacultyManagedCourseResponse> taught = context.remember(MANAGED_COURSES,
                () -> managedCourses.getManagedCourses(context.actor().accountId()));
        List<SearchResult> matches = taught.stream()
                .filter(course -> text.matches(course.courseCode()) || text.matches(course.courseName())
                        || text.matches(course.programCode()) || text.matches(course.academicSessionCode()))
                .sorted(Comparator.<LmsFacultyManagedCourseResponse>comparingInt(
                                course -> Math.min(text.rank(course.courseCode()), text.rank(course.courseName())))
                        .thenComparing(LmsFacultyManagedCourseResponse::courseCode)
                        .thenComparing(LmsFacultyManagedCourseResponse::courseOfferingId))
                .map(course -> new SearchResult(SearchResultType.TEACHING_COURSE, course.courseName(),
                        SearchFormat.join(course.courseCode(), course.programCode(), "Semester " + course.semesterNumber(),
                                course.academicSessionCode(), lmsState(course.lmsCourseStatus())),
                        null, teachingPath(course), null))
                .toList();
        return pageOf(matches, page);
    }

    private Page<SearchResult> teachingAssignments(SearchContext context, Pageable page) {
        Map<Long, LmsFacultyCourseSummaryResponse> courses = facultyCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return assignments.search(courses.keySet(), LmsContentPolicy.IN_USE, text.exact(), text.prefix(),
                text.contains(), page).map(assignment -> {
                    LmsFacultyCourseSummaryResponse course = courses.get(courseIdOf(assignment));
                    return new SearchResult(SearchResultType.TEACHING_ASSIGNMENT, assignment.getTitle(),
                            SearchFormat.join(courseLabel(course.courseCode(), course.courseName()),
                                    SearchFormat.words(assignment.getStatus())),
                            null, "/teaching/" + course.lmsCourseId(), assignment.getDueAt());
                });
    }

    private Page<SearchResult> teachingAnnouncements(SearchContext context, Pageable page) {
        Map<Long, LmsFacultyCourseSummaryResponse> courses = facultyCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return announcements.search(courses.keySet(), LmsContentPolicy.IN_USE, text.exact(), text.prefix(),
                text.contains(), page).map(announcement -> {
                    LmsFacultyCourseSummaryResponse course = courses.get(courseIdOf(announcement));
                    return new SearchResult(SearchResultType.TEACHING_ANNOUNCEMENT, announcement.getTitle(),
                            SearchFormat.join(courseLabel(course.courseCode(), course.courseName()),
                                    SearchFormat.words(announcement.getStatus())),
                            SearchFormat.snippet(announcement.getMessage()), "/teaching/" + course.lmsCourseId(),
                            announcement.getPublishedAt());
                });
    }

    private Page<SearchResult> teachingMaterials(SearchContext context, Pageable page) {
        Map<Long, LmsFacultyCourseSummaryResponse> courses = facultyCourseMap(context);
        if (courses.isEmpty()) {
            return Page.empty(page);
        }
        SearchText text = context.text();
        return materials.search(courses.keySet(), LmsContentPolicy.IN_USE, text.exact(), text.prefix(),
                text.contains(), page).map(material -> {
                    LmsFacultyCourseSummaryResponse course = courses.get(courseIdOf(material));
                    return new SearchResult(SearchResultType.TEACHING_MATERIAL, material.getTitle(),
                            SearchFormat.join(courseLabel(course.courseCode(), course.courseName()),
                                    material.getOriginalFileName(), SearchFormat.words(material.getStatus())),
                            SearchFormat.snippet(material.getDescription()), "/teaching/" + course.lmsCourseId(), null);
                });
    }

    // --- Helpers

    /** A taught offering opens its teaching page only while its LMS course can be used; otherwise the Teaching list. */
    static String teachingPath(LmsFacultyManagedCourseResponse course) {
        boolean open = course.lmsCourseId() != null && course.lmsCourseStatus() != null
                && LmsCourseStatus.valueOf(course.lmsCourseStatus()) != LmsCourseStatus.ARCHIVED;
        return open ? "/teaching/" + course.lmsCourseId() : "/teaching";
    }

    private static String lmsState(String status) {
        if (status == null) {
            return "No LMS course yet";
        }
        return switch (LmsCourseStatus.valueOf(status)) {
            case DRAFT -> "LMS draft";
            case PUBLISHED -> "LMS published";
            case ARCHIVED -> "LMS archived";
        };
    }

    private static String courseLabel(String code, String name) {
        return code + " " + name;
    }

    // The course is a lazy reference; reading its ID loads nothing.
    private static Long courseIdOf(LmsAssignment assignment) {
        return assignment.getLmsCourse().getId();
    }

    private static Long courseIdOf(LmsAnnouncement announcement) {
        return announcement.getLmsCourse().getId();
    }

    private static Long courseIdOf(LmsStudyMaterial material) {
        return material.getLmsTopic().getLmsModule().getLmsCourse().getId();
    }

    /** One page of a short list that was already matched and ordered in memory. */
    static <T> Page<T> pageOf(List<T> all, Pageable page) {
        int from = (int) Math.min(page.getOffset(), all.size());
        int to = Math.min(from + page.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), page, all.size());
    }
}
