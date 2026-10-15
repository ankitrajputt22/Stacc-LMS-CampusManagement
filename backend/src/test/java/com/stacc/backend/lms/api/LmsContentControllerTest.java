package com.stacc.backend.lms.api;

import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentManagementService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the real content routes through Spring Security. The services are stand-ins, so these tests are
 * about who gets in, what is checked at the door, and what is sent back. What each side may see or
 * change is tested with the services themselves.
 */
class LmsContentControllerTest extends DatabaseFreeApiTest {

    // Made-up values used only by these tests.
    private static final long ACCOUNT_ID = 21;
    private static final String VALID_MODULE = "{\"title\":\"Introduction\",\"description\":\"Read this first.\"}";
    private static final String VALID_TOPIC = "{\"title\":\"Course overview\",\"content\":\"Line one\\nLine two\"}";

    @MockitoBean
    private LmsStudentContentQueryService studentContent;

    @MockitoBean
    private LmsFacultyContentQueryService facultyContent;

    @MockitoBean
    private LmsContentManagementService contentManagement;

    private final LmsCourse course = ContentFixtures.lmsCourse(7);
    private final LmsModule module = module(course, 12, 1, LmsContentStatus.DRAFT);
    private final LmsTopic topic = topic(module, 31, 1, LmsContentStatus.DRAFT);

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW");
    }

    private static String facultyViewToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW");
    }

    private static String facultyManageToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Every faculty change route, with a valid body where one is needed. */
    private static List<MockHttpServletRequestBuilder> changeRoutes() {
        return List.of(
                json(post("/api/lms/faculty/courses/7/modules"), VALID_MODULE),
                json(put("/api/lms/faculty/courses/7/modules/reorder"), "{\"moduleIds\":[12]}"),
                json(put("/api/lms/faculty/modules/12"), VALID_MODULE),
                post("/api/lms/faculty/modules/12/publish"),
                post("/api/lms/faculty/modules/12/archive"),
                json(post("/api/lms/faculty/modules/12/topics"), VALID_TOPIC),
                json(put("/api/lms/faculty/modules/12/topics/reorder"), "{\"topicIds\":[31]}"),
                json(put("/api/lms/faculty/topics/31"), VALID_TOPIC),
                post("/api/lms/faculty/topics/31/publish"),
                post("/api/lms/faculty/topics/31/archive"));
    }

    // Student content

    @Test
    void studentGetsThePublishedContentOfTheirCourse() throws Exception {
        when(studentContent.getPublishedContent(ACCOUNT_ID, 7L)).thenReturn(new LmsCourseContentResponse(List.of(
                new LmsCourseModuleResponse(12L, "Introduction", null,
                        List.of(new LmsCourseTopicResponse(31L, "Overview", "Line one\nLine two", List.of()))))));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/content").param("accountId", "999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", hasSize(1)))
                .andExpect(jsonPath("$.modules[0].*", hasSize(4)))
                .andExpect(jsonPath("$.modules[0].title").value("Introduction"))
                .andExpect(jsonPath("$.modules[0].topics[0].*", hasSize(4)))
                .andExpect(jsonPath("$.modules[0].topics[0].materials", hasSize(0)))
                .andExpect(jsonPath("$.modules[0].topics[0].content").value("Line one\nLine two"))
                .andExpect(content().string(not(containsString("status"))))
                .andExpect(content().string(not(containsString("position"))));

        verify(studentContent).getPublishedContent(ACCOUNT_ID, 7L);
    }

    @Test
    void studentContentRouteNeedsTheStudentRoleAndPermission() throws Exception {
        mockMvc.perform(get("/api/lms/courses/7/content")).andExpect(status().isUnauthorized());
        for (String token : List.of(
                tokenWith("ROLE_STUDENT"),
                tokenWith("LMS_COURSE_VIEW"),
                facultyManageToken(),
                tokenWith("ROLE_FACULTY", "LMS_COURSE_VIEW"),
                tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW"))) {
            mockMvc.perform(as(token, get("/api/lms/courses/7/content"))).andExpect(status().isForbidden());
        }

        verifyNoInteractions(studentContent);
    }

    @Test
    void unavailableCourseContentKeepsTheSafeMessage() throws Exception {
        when(studentContent.getPublishedContent(ACCOUNT_ID, 7L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Course not found or unavailable."));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/content")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Course not found or unavailable."));
    }

    // Faculty content

    @Test
    void facultyMemberGetsTheContentTheyManage() throws Exception {
        when(facultyContent.getCourseContent(ACCOUNT_ID, 7L)).thenReturn(new LmsFacultyCourseContentResponse(
                7L, "TST301", "Test Data Structures", null, 3, "BTECH", "2026-27", "DRAFT", List.of(
                        LmsFacultyModuleContentResponse.of(module, List.of(LmsFacultyTopicContentResponse.of(topic, List.of()))))));

        mockMvc.perform(as(facultyViewToken(), get("/api/lms/faculty/courses/7/content").param("facultyProfileId", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lmsCourseStatus").value("DRAFT"))
                .andExpect(jsonPath("$.modules[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.modules[0].topics[0].id").value(31))
                .andExpect(jsonPath("$.modules[0].topics[0].position").value(1))
                .andExpect(content().string(not(containsString("courseOfferingId"))))
                .andExpect(content().string(not(containsString("teachingAssignment"))));

        verify(facultyContent).getCourseContent(ACCOUNT_ID, 7L);
    }

    @Test
    void facultyContentRouteNeedsTheFacultyRoleAndViewPermission() throws Exception {
        mockMvc.perform(get("/api/lms/faculty/courses/7/content")).andExpect(status().isUnauthorized());
        for (String token : List.of(
                tokenWith("ROLE_FACULTY"),
                tokenWith("ROLE_FACULTY", "LMS_CONTENT_MANAGE"),
                tokenWith("FACULTY_COURSE_VIEW"),
                studentToken(),
                tokenWith("ROLE_STUDENT", "FACULTY_COURSE_VIEW"),
                tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"))) {
            mockMvc.perform(as(token, get("/api/lms/faculty/courses/7/content"))).andExpect(status().isForbidden());
        }

        verifyNoInteractions(facultyContent);
    }

    // Faculty changes

    @Test
    void addingAModuleAnswersCreatedWithTheNewModule() throws Exception {
        when(contentManagement.createModule(ACCOUNT_ID, 7L, "Introduction", "Read this first.")).thenReturn(module);

        mockMvc.perform(as(facultyManageToken(), json(post("/api/lms/faculty/courses/7/modules"),
                        "{\"title\":\"Introduction\",\"description\":\"Read this first.\","
                                + "\"status\":\"PUBLISHED\",\"position\":9,\"lmsCourseId\":99}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.*", hasSize(5)));

        verify(contentManagement).createModule(ACCOUNT_ID, 7L, "Introduction", "Read this first.");
    }

    @Test
    void addingATopicAnswersCreatedWithTheNewTopic() throws Exception {
        when(contentManagement.createTopic(ACCOUNT_ID, 12L, "Course overview", "Line one\nLine two")).thenReturn(topic);

        mockMvc.perform(as(facultyManageToken(), json(post("/api/lms/faculty/modules/12/topics"), VALID_TOPIC)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void eachChangeRouteCallsItsActionForTheAccountInTheToken() throws Exception {
        when(contentManagement.reorderModules(ACCOUNT_ID, 7L, List.of(12L))).thenReturn(List.of(module));
        when(contentManagement.updateModule(ACCOUNT_ID, 12L, "Introduction", "Read this first.")).thenReturn(module);
        when(contentManagement.publishModule(ACCOUNT_ID, 12L)).thenReturn(module);
        when(contentManagement.archiveModule(ACCOUNT_ID, 12L)).thenReturn(module);
        when(contentManagement.reorderTopics(ACCOUNT_ID, 12L, List.of(31L))).thenReturn(List.of(topic));
        when(contentManagement.updateTopic(ACCOUNT_ID, 31L, "Course overview", "Line one\nLine two")).thenReturn(topic);
        when(contentManagement.publishTopic(ACCOUNT_ID, 31L)).thenReturn(topic);
        when(contentManagement.archiveTopic(ACCOUNT_ID, 31L)).thenReturn(topic);

        // Adding a module or a topic has its own tests above.
        for (MockHttpServletRequestBuilder route : List.of(
                json(put("/api/lms/faculty/courses/7/modules/reorder"), "{\"moduleIds\":[12]}"),
                json(put("/api/lms/faculty/modules/12"), VALID_MODULE),
                post("/api/lms/faculty/modules/12/publish"),
                post("/api/lms/faculty/modules/12/archive"),
                json(put("/api/lms/faculty/modules/12/topics/reorder"), "{\"topicIds\":[31]}"),
                json(put("/api/lms/faculty/topics/31"), VALID_TOPIC),
                post("/api/lms/faculty/topics/31/publish"),
                post("/api/lms/faculty/topics/31/archive"))) {
            mockMvc.perform(as(facultyManageToken(), route)).andExpect(status().isOk());
        }

        verify(contentManagement).reorderModules(ACCOUNT_ID, 7L, List.of(12L));
        verify(contentManagement).updateModule(ACCOUNT_ID, 12L, "Introduction", "Read this first.");
        verify(contentManagement).publishModule(ACCOUNT_ID, 12L);
        verify(contentManagement).archiveModule(ACCOUNT_ID, 12L);
        verify(contentManagement).reorderTopics(ACCOUNT_ID, 12L, List.of(31L));
        verify(contentManagement).updateTopic(ACCOUNT_ID, 31L, "Course overview", "Line one\nLine two");
        verify(contentManagement).publishTopic(ACCOUNT_ID, 31L);
        verify(contentManagement).archiveTopic(ACCOUNT_ID, 31L);
    }

    @Test
    void changesNeedTheFacultyRoleAndTheManagePermission() throws Exception {
        for (MockHttpServletRequestBuilder route : changeRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                facultyViewToken(),
                tokenWith("LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_ADMIN", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "ROLE_LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "SCOPE_LMS_CONTENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : changeRoutes()) {
                mockMvc.perform(as(token, route))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."));
            }
        }

        verifyNoInteractions(contentManagement);
    }

    @Test
    void invalidBodiesAreRefusedBeforeAnyChange() throws Exception {
        String longTitle = "x".repeat(201);
        for (MockHttpServletRequestBuilder route : List.of(
                json(post("/api/lms/faculty/courses/7/modules"), "{\"title\":\"  \"}"),
                json(post("/api/lms/faculty/courses/7/modules"), "{\"title\":\"" + longTitle + "\"}"),
                json(post("/api/lms/faculty/courses/7/modules"), "{\"title\":\"A\",\"description\":\"" + "x".repeat(2001) + "\"}"),
                json(put("/api/lms/faculty/modules/12"), "{}"),
                json(post("/api/lms/faculty/modules/12/topics"), "{\"title\":\"A\",\"content\":\"" + "x".repeat(20_001) + "\"}"),
                json(put("/api/lms/faculty/topics/31"), "{\"content\":\"No title\"}"),
                json(put("/api/lms/faculty/courses/7/modules/reorder"), "{}"),
                json(put("/api/lms/faculty/courses/7/modules/reorder"), "{\"moduleIds\":[12,null]}"),
                json(put("/api/lms/faculty/modules/12/topics/reorder"), "{\"topicIds\":null}"))) {
            mockMvc.perform(as(facultyManageToken(), route))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        mockMvc.perform(as(facultyManageToken(), json(post("/api/lms/faculty/courses/7/modules"), "{not json")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(facultyManageToken(), post("/api/lms/faculty/modules/abc/publish")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(contentManagement);
    }

    @Test
    void refusalsFromTheServiceKeepTheirStatusAndSafeMessage() throws Exception {
        when(contentManagement.publishModule(anyLong(), eq(12L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Module not found or unavailable."));
        when(contentManagement.updateTopic(anyLong(), eq(31L), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This topic is archived and can no longer be changed."));
        when(contentManagement.reorderModules(anyLong(), eq(7L), anyList()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "List every current module of this course exactly once."));

        mockMvc.perform(as(facultyManageToken(), post("/api/lms/faculty/modules/12/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Module not found or unavailable."));
        mockMvc.perform(as(facultyManageToken(), json(put("/api/lms/faculty/topics/31"), VALID_TOPIC)))
                .andExpect(status().isConflict());
        mockMvc.perform(as(facultyManageToken(), json(put("/api/lms/faculty/courses/7/modules/reorder"), "{\"moduleIds\":[1]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("List every current module of this course exactly once."));
    }

    @Test
    void contentCannotBeDeleted() throws Exception {
        for (String path : List.of("/api/lms/faculty/modules/12", "/api/lms/faculty/topics/31")) {
            mockMvc.perform(as(facultyManageToken(), delete(path)))
                    .andExpect(status().isMethodNotAllowed());
        }

        verifyNoInteractions(contentManagement);
    }

    @Test
    void contentRoutesAppearInTheApiDocumentationAsBearerTokenOperations() throws Exception {
        String[][] operations = {
                {"/api/lms/courses/{lmsCourseId}/content", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/content", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/modules", "post", "201"},
                {"/api/lms/faculty/courses/{lmsCourseId}/modules/reorder", "put", "200"},
                {"/api/lms/faculty/modules/{moduleId}", "put", "200"},
                {"/api/lms/faculty/modules/{moduleId}/publish", "post", "200"},
                {"/api/lms/faculty/modules/{moduleId}/archive", "post", "200"},
                {"/api/lms/faculty/modules/{moduleId}/topics", "post", "201"},
                {"/api/lms/faculty/modules/{moduleId}/topics/reorder", "put", "200"},
                {"/api/lms/faculty/topics/{topicId}", "put", "200"},
                {"/api/lms/faculty/topics/{topicId}/publish", "post", "200"},
                {"/api/lms/faculty/topics/{topicId}/archive", "post", "200"}};
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : operations) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['" + operation[2] + "']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists())
                    .andExpect(jsonPath(path + ".responses['404']").exists());
        }
        result.andExpect(jsonPath("$.paths['/api/lms/faculty/modules/{moduleId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/topics/{topicId}'].delete").doesNotExist());
    }
}
