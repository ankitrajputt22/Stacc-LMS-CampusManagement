package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.quiz.attempt.LmsQuizGradingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty with the quiz grading permission read results or mark; marks are checked before the service. */
class LmsQuizGradingControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private LmsQuizGradingService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 21L, authorities);
    }

    private static MockHttpServletRequestBuilder mark(String body) {
        return put("/api/v1/lms/faculty/quiz-attempts/9/answers/601/mark").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void everyRouteNeedsTheFacultyRoleCourseViewAndTheGradingPermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(get("/api/v1/lms/faculty/quizzes/501/attempts"),
                get("/api/v1/lms/faculty/quiz-attempts/9"), mark("{\"marks\":1}"), post("/api/v1/lms/faculty/quiz-attempts/9/close"));
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUIZ_MANAGE", "LMS_ASSIGNMENT_GRADE"),
                token("ROLE_FACULTY", "LMS_QUIZ_GRADE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_QUIZ_TAKE", "FACULTY_COURSE_VIEW", "LMS_QUIZ_GRADE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_QUIZ_GRADE"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void marksAreCheckedBeforeTheService() throws Exception {
        String grader = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUIZ_GRADE");
        for (String bad : List.of("{}", "{\"marks\":-1}", "{\"marks\":1.234}", "{\"marks\":1,\"feedback\":\"" + "x".repeat(10_001) + "\"}")) {
            mockMvc.perform(as(grader, mark(bad))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
