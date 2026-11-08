package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.gradebook.LmsGradebookService;
import com.stacc.backend.lms.gradebook.LmsStudentGradesService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Faculty need the gradebook permission and students the grades permission; requests are checked before the services. */
class LmsGradebookControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private LmsGradebookService gradebook;

    @MockitoBean
    private LmsStudentGradesService studentGrades;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 21L, authorities);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void facultyRoutesNeedTheFacultyRoleCourseViewAndTheGradebookPermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/lms/faculty/courses/7/gradebook"),
                json(post("/api/v1/lms/faculty/courses/7/gradebook/items"), "{\"title\":\"Test\",\"maxMarks\":10}"),
                json(put("/api/v1/lms/faculty/gradebook/items/70"), "{\"title\":\"Test\",\"maxMarks\":10}"),
                json(post("/api/v1/lms/faculty/courses/7/gradebook/publish"), "{\"type\":\"MANUAL\",\"sourceId\":70}"),
                json(put("/api/v1/lms/faculty/gradebook/items/70/entries/41"), "{\"marks\":5}"));
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUIZ_GRADE", "LMS_ASSIGNMENT_GRADE"),
                token("ROLE_FACULTY", "LMS_GRADEBOOK_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_GRADE_VIEW", "FACULTY_COURSE_VIEW", "LMS_GRADEBOOK_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_GRADEBOOK_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(gradebook);
    }

    @Test
    void theStudentRouteNeedsTheStudentRoleCourseViewAndTheGradesPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), token("ROLE_STUDENT", "LMS_GRADE_VIEW"),
                token("ROLE_FACULTY", "LMS_COURSE_VIEW", "LMS_GRADE_VIEW", "LMS_GRADEBOOK_MANAGE"))) {
            mockMvc.perform(as(wrong, get("/api/v1/lms/courses/7/grades"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(studentGrades);
    }

    @Test
    void requestsAreCheckedBeforeTheService() throws Exception {
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_GRADEBOOK_MANAGE");
        for (MockHttpServletRequestBuilder bad : List.of(
                json(post("/api/v1/lms/faculty/courses/7/gradebook/items"), "{\"title\":\" \",\"maxMarks\":10}"),
                json(post("/api/v1/lms/faculty/courses/7/gradebook/items"), "{\"title\":\"Test\",\"maxMarks\":0}"),
                json(post("/api/v1/lms/faculty/courses/7/gradebook/publish"), "{\"type\":\"EXAM\",\"sourceId\":1}"),
                json(post("/api/v1/lms/faculty/courses/7/gradebook/publish"), "{\"type\":\"MANUAL\"}"),
                json(put("/api/v1/lms/faculty/gradebook/items/70/entries/41"), "{\"marks\":-1}"))) {
            mockMvc.perform(as(manager, bad)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(gradebook);
    }
}
