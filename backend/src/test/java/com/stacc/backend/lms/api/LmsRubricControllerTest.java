package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.assignment.rubric.LmsRubricService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Rubrics need the assignment management permission and rubric grading the grading permission; bodies are checked first. */
class LmsRubricControllerTest extends DatabaseFreeApiTest {

    private static final String RUBRIC = """
            {"title":"Design","criteria":[{"title":"ER diagram","maxPoints":10,"levels":[{"title":"Excellent","points":10}]}]}
            """;

    @MockitoBean
    private LmsRubricService rubrics;

    @MockitoBean
    private LmsAssignmentManagementService assignments;

    @MockitoBean
    private LmsAssignmentGradingService grading;

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
    void rubricRoutesNeedTheFacultyRoleCourseViewAndAssignmentManagement() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/lms/faculty/courses/7/rubrics"),
                json(post("/api/v1/lms/faculty/courses/7/rubrics"), RUBRIC), json(put("/api/v1/lms/faculty/rubrics/4"), RUBRIC),
                post("/api/v1/lms/faculty/rubrics/4/archive"), json(put("/api/v1/lms/faculty/assignments/5/rubric"), "{\"rubricId\":4}"));
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE", "LMS_QUIZ_MANAGE"),
                token("ROLE_FACULTY", "LMS_ASSIGNMENT_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(rubrics, assignments);
    }

    @Test
    void rubricGradingNeedsTheGradingPermissionAndCompleteScores() throws Exception {
        MockHttpServletRequestBuilder route = json(put("/api/v1/lms/faculty/submission-attempts/301/rubric-grade"),
                "{\"scores\":[{\"criterionId\":40,\"points\":8}]}");
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"), route)).andExpect(status().isForbidden());
        String grader = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE");
        for (String bad : List.of("{\"scores\":[]}", "{\"scores\":[{\"criterionId\":40}]}", "{\"scores\":[{\"points\":1}]}",
                "{\"scores\":[{\"criterionId\":40,\"points\":-1}]}", "{\"scores\":[{\"criterionId\":40,\"points\":1.234}]}")) {
            mockMvc.perform(as(grader, json(put("/api/v1/lms/faculty/submission-attempts/301/rubric-grade"), bad)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(grading);
    }

    @Test
    void rubricsAreCheckedBeforeTheService() throws Exception {
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE");
        for (String bad : List.of("{\"title\":\"R\",\"criteria\":[]}", RUBRIC.replace("\"maxPoints\":10", "\"maxPoints\":0"),
                RUBRIC.replace("\"title\":\"ER diagram\",", ""), RUBRIC.replace("\"points\":10", "\"points\":-1"))) {
            mockMvc.perform(as(manager, json(post("/api/v1/lms/faculty/courses/7/rubrics"), bad))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(rubrics);
    }
}
