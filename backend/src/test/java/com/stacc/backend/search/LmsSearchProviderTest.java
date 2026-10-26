package com.stacc.backend.search;

import static com.stacc.backend.search.SearchTestSupport.actor;
import static com.stacc.backend.search.SearchTestSupport.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class LmsSearchProviderTest {

    private final LmsStudentCourseQueryService studentCourses = mock(LmsStudentCourseQueryService.class);
    private final LmsFacultyCourseQueryService facultyCourses = mock(LmsFacultyCourseQueryService.class);
    private final LmsFacultyManagedCourseQueryService managedCourses = mock(LmsFacultyManagedCourseQueryService.class);
    private final LmsAssignmentRepository assignments = mock(LmsAssignmentRepository.class);
    private final LmsAnnouncementRepository announcements = mock(LmsAnnouncementRepository.class);
    private final LmsStudyMaterialRepository materials = mock(LmsStudyMaterialRepository.class);
    private final LmsSearchProvider provider = new LmsSearchProvider(studentCourses, facultyCourses, managedCourses,
            assignments, announcements, materials);

    private final SearchActor student = actor(70, Set.of("STUDENT"), "LMS_COURSE_VIEW", "GLOBAL_SEARCH_USE");
    private final SearchActor faculty = actor(80, Set.of("FACULTY"), "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE");
    private final PageRequest five = PageRequest.of(0, 5);

    private static LmsCourseSummaryResponse course(long id, String code, String name) {
        return new LmsCourseSummaryResponse(id, code, name, new BigDecimal("4.00"), 3, "BTECH-CSE", "2026-27");
    }

    private static LmsAssignment assignment(long id, long courseId, String title, LmsContentStatus status) {
        LmsCourse course = mock(LmsCourse.class);
        when(course.getId()).thenReturn(courseId);
        LmsAssignment assignment = mock(LmsAssignment.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getLmsCourse()).thenReturn(course);
        when(assignment.getTitle()).thenReturn(title);
        when(assignment.getStatus()).thenReturn(status);
        when(assignment.getDueAt()).thenReturn(Instant.parse("2026-11-04T17:30:00Z"));
        return assignment;
    }

    private static LmsStudyMaterial material(long courseId, LmsContentStatus materialStatus, LmsContentStatus topicStatus,
            LmsContentStatus moduleStatus) {
        LmsCourse course = mock(LmsCourse.class);
        when(course.getId()).thenReturn(courseId);
        LmsModule module = mock(LmsModule.class);
        when(module.getLmsCourse()).thenReturn(course);
        when(module.getStatus()).thenReturn(moduleStatus);
        LmsTopic topic = mock(LmsTopic.class);
        when(topic.getLmsModule()).thenReturn(module);
        when(topic.getStatus()).thenReturn(topicStatus);
        LmsStudyMaterial material = mock(LmsStudyMaterial.class);
        when(material.getLmsTopic()).thenReturn(topic);
        when(material.getStatus()).thenReturn(materialStatus);
        when(material.getTitle()).thenReturn("Lecture notes");
        when(material.getOriginalFileName()).thenReturn("notes.pdf");
        return material;
    }

    @BeforeEach
    void setUp() {
        // Course A is the student's; course B exists but the student may not use it, so the course service never lists it.
        when(studentCourses.getAccessibleCourses(70L)).thenReturn(List.of(course(11, "CS301", "Data Structures")));
    }

    @Test
    void studentCoursesAreOnlyTheAccessibleOnesRankedExactThenPrefixThenContains() {
        when(studentCourses.getAccessibleCourses(71L)).thenReturn(List.of(course(1, "MA101", "Big Data Basics"),
                course(2, "CS200", "Data"), course(3, "CS301", "Data Structures"), course(4, "PH101", "Physics")));

        var page = provider.search(SearchResultType.COURSE,
                context(actor(71, Set.of("STUDENT"), "LMS_COURSE_VIEW"), "data"), five).orElseThrow();

        assertThat(page.getContent()).extracting(SearchResult::title)
                .containsExactly("Data", "Data Structures", "Big Data Basics");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().get(0).actionPath()).isEqualTo("/courses/2");
        assertThat(page.getContent().get(0).subtitle()).isEqualTo("CS200 · BTECH-CSE · Semester 3 · 2026-27");
    }

    @Test
    void studentContentQueriesOnlyTheAccessibleCoursesAndPublishedContent() {
        LmsAssignment linkedList = assignment(7, 11, "Linked list", LmsContentStatus.PUBLISHED);
        when(assignments.search(any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(linkedList), five, 1));
        when(announcements.search(any(), any(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(materials.search(any(), any(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        SearchContext context = context(student, "list");

        var page = provider.search(SearchResultType.ASSIGNMENT, context, five).orElseThrow();
        provider.search(SearchResultType.ANNOUNCEMENT, context, five);
        provider.search(SearchResultType.STUDY_MATERIAL, context, five);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(assignments).search(ids.capture(), eq(LmsContentPolicy.PUBLISHED_ONLY), eq("list"), eq("list%"),
                eq("%list%"), eq(five));
        assertThat(ids.getValue()).containsExactly(11L);
        verify(announcements).search(eq(Set.of(11L)), eq(LmsContentPolicy.PUBLISHED_ONLY), any(), any(), any(), any());
        verify(materials).search(eq(Set.of(11L)), eq(LmsContentPolicy.PUBLISHED_ONLY), any(), any(), any(), any());
        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.actionPath()).isEqualTo("/courses/11/assignments/7");
            assertThat(result.subtitle()).isEqualTo("CS301 Data Structures");
        });
        // The accessible courses are looked up once per search, not once per section.
        verify(studentCourses, times(1)).getAccessibleCourses(70L);
    }

    @Test
    void aStudentWithoutAccessibleCoursesSendsNoContentQuery() {
        when(studentCourses.getAccessibleCourses(72L)).thenReturn(List.of());
        SearchContext context = context(actor(72, Set.of("STUDENT"), "LMS_COURSE_VIEW"), "data");

        for (SearchResultType type : List.of(SearchResultType.ASSIGNMENT, SearchResultType.ANNOUNCEMENT,
                SearchResultType.STUDY_MATERIAL, SearchResultType.COURSE)) {
            assertThat(provider.search(type, context, five).orElseThrow().getTotalElements()).isZero();
        }
        verifyNoInteractions(assignments, announcements, materials);
    }

    @Test
    void aMaterialOutsideThePublishedChainIsNeverShownToStudents() {
        LmsStudyMaterial published = material(11, LmsContentStatus.PUBLISHED, LmsContentStatus.PUBLISHED,
                LmsContentStatus.PUBLISHED);
        when(materials.search(any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(published), five, 1));

        assertThat(provider.search(SearchResultType.STUDY_MATERIAL, context(student, "notes"), five).orElseThrow()
                .getContent()).singleElement().satisfies(result -> {
                    assertThat(result.actionPath()).isEqualTo("/courses/11");
                    assertThat(result.subtitle()).isEqualTo("CS301 Data Structures · notes.pdf");
                });

        // Should the query ever return a draft topic's material, the content policy refuses it rather than showing it.
        LmsStudyMaterial draftTopic = material(11, LmsContentStatus.PUBLISHED, LmsContentStatus.DRAFT,
                LmsContentStatus.PUBLISHED);
        when(materials.search(any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(draftTopic), five, 1));
        assertThatThrownBy(() -> provider.search(SearchResultType.STUDY_MATERIAL,
                context(student, "notes"), five)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutTheStudentRoleOrLmsCourseViewThereAreNoStudentSections() {
        for (SearchActor actor : List.of(actor(70, Set.of("STUDENT"), "GLOBAL_SEARCH_USE"),
                actor(70, Set.of("FACULTY", "ADMIN"), "LMS_COURSE_VIEW"))) {
            for (SearchResultType type : List.of(SearchResultType.COURSE, SearchResultType.ASSIGNMENT,
                    SearchResultType.ANNOUNCEMENT, SearchResultType.STUDY_MATERIAL)) {
                assertThat(provider.search(type, context(actor, "data"), five)).isEmpty();
            }
        }
        verify(studentCourses, never()).getAccessibleCourses(anyLong());
    }

    @Test
    void facultyContentIsDraftOrPublishedInTheirOwnOpenCoursesOnly() {
        when(facultyCourses.getAccessibleCourses(80L)).thenReturn(List.of(new LmsFacultyCourseSummaryResponse(21L,
                "CS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH-CSE", "2026-27", "DRAFT")));
        LmsAssignment graphLab = assignment(9, 21, "Graph lab", LmsContentStatus.DRAFT);
        when(assignments.search(any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(graphLab), five, 1));

        var page = provider.search(SearchResultType.TEACHING_ASSIGNMENT, context(faculty, "lab"), five).orElseThrow();

        verify(assignments).search(eq(Set.of(21L)), eq(LmsContentPolicy.IN_USE), any(), any(), any(), any());
        assertThat(page.getContent()).singleElement().satisfies(result -> {
            assertThat(result.type()).isEqualTo(SearchResultType.TEACHING_ASSIGNMENT);
            assertThat(result.actionPath()).isEqualTo("/teaching/21");
            assertThat(result.subtitle()).isEqualTo("CS301 Data Structures · Draft");
        });
        verify(studentCourses, never()).getAccessibleCourses(anyLong());
    }

    @Test
    void teachingCoursesIncludeOfferingsWithoutAnLmsCourseAndArchivedOnesOpenTheTeachingList() {
        when(managedCourses.getManagedCourses(80L)).thenReturn(List.of(
                new LmsFacultyManagedCourseResponse(31L, "CS301", "Data Structures", BigDecimal.ONE, 3, "BTECH", "2026-27", 21L, "DRAFT"),
                new LmsFacultyManagedCourseResponse(32L, "CS302", "Databases", BigDecimal.ONE, 3, "BTECH", "2026-27", null, null),
                new LmsFacultyManagedCourseResponse(33L, "CS303", "Data Mining", BigDecimal.ONE, 3, "BTECH", "2025-26", 23L, "ARCHIVED")));

        var page = provider.search(SearchResultType.TEACHING_COURSE, context(faculty, "CS30"), five).orElseThrow();

        assertThat(page.getContent()).extracting(SearchResult::actionPath)
                .containsExactly("/teaching/21", "/teaching", "/teaching");
        assertThat(page.getContent().get(1).subtitle()).endsWith("No LMS course yet");
        // The Teaching list needs LMS_COURSE_MANAGE as well.
        assertThat(provider.search(SearchResultType.TEACHING_COURSE,
                context(actor(80, Set.of("FACULTY"), "FACULTY_COURSE_VIEW"), "CS30"), five)).isEmpty();
    }

    @Test
    void aStudentAndFacultyAccountGetsBothWorkflowsSeparately() {
        SearchActor both = actor(90, Set.of("STUDENT", "FACULTY"), "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW",
                "LMS_COURSE_MANAGE");
        when(studentCourses.getAccessibleCourses(90L)).thenReturn(List.of(course(11, "CS301", "Data Structures")));
        when(managedCourses.getManagedCourses(90L)).thenReturn(List.of(new LmsFacultyManagedCourseResponse(31L, "CS301",
                "Data Structures", BigDecimal.ONE, 3, "BTECH", "2026-27", 21L, "PUBLISHED")));
        SearchContext context = context(both, "CS301");

        assertThat(provider.search(SearchResultType.COURSE, context, five).orElseThrow().getContent())
                .extracting(SearchResult::actionPath).containsExactly("/courses/11");
        assertThat(provider.search(SearchResultType.TEACHING_COURSE, context, five).orElseThrow().getContent())
                .extracting(SearchResult::actionPath).containsExactly("/teaching/21");
    }

    @Test
    void anInMemoryPageBeyondTheEndIsEmptyWithTheRightTotal() {
        var page = LmsSearchProvider.pageOf(List.of("a", "b", "c"), PageRequest.of(5, 2));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
