package com.stacc.backend.lms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.LmsQuizService;
import com.stacc.backend.lms.quiz.LmsQuizSettings;
import com.stacc.backend.lms.quiz.QuizFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty with the quiz permission read or change quizzes; settings are checked before the service. */
class LmsQuizControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final String QUIZ = """
            {"title":"Normalization check","instructions":"Answer every question.","instructionsFormat":"FORMATTED",
             "type":"GRADED","timeLimitMinutes":20,"availableFrom":"2026-11-20T03:30:00Z","dueAt":"2026-11-21T18:29:00Z",
             "availableUntil":"2026-11-22T18:29:00Z","maxAttempts":1,"shuffleQuestions":true,"shuffleOptions":false,
             "negativeMarkPercent":25}
            """;

    @MockitoBean
    private LmsQuizService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(
                get("/api/v1/lms/faculty/courses/7/quizzes"),
                json(post("/api/v1/lms/faculty/courses/7/quizzes"), QUIZ),
                get("/api/v1/lms/faculty/quizzes/501"),
                json(put("/api/v1/lms/faculty/quizzes/501"), QUIZ),
                json(post("/api/v1/lms/faculty/quizzes/501/questions"), "{\"questionId\":41}"),
                json(put("/api/v1/lms/faculty/quizzes/501/questions/601"), "{\"marks\":2}"),
                delete("/api/v1/lms/faculty/quizzes/501/questions/601"),
                json(put("/api/v1/lms/faculty/quizzes/501/question-order"), "{\"quizQuestionIds\":[601]}"),
                post("/api/v1/lms/faculty/quizzes/501/publish"),
                post("/api/v1/lms/faculty/quizzes/501/archive"));
    }

    @Test
    void everyRouteNeedsTheFacultyRoleCourseViewAndTheQuizPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE", "LMS_CONTENT_MANAGE"),
                token("ROLE_FACULTY", "LMS_QUIZ_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_QUIZ_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_QUIZ_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void aQuizIsSentWithItsSettingsQuestionsAndTotal() throws Exception {
        LmsQuiz quiz = new LmsQuiz(ContentFixtures.lmsCourse(7), ACCOUNT_ID, QuizFixtures.graded("Unit test"));
        ReflectionTestUtils.setField(quiz, "id", 501L);
        LmsQuestion question = new LmsQuestion(ContentFixtures.lmsCourse(7), ACCOUNT_ID, QuestionFixtures.singleChoice("Which?"));
        LmsQuizQuestion link = new LmsQuizQuestion(quiz, question, 1, new BigDecimal("2.5"));
        when(service.create(eq(ACCOUNT_ID), eq(7L), any(LmsQuizSettings.class))).thenReturn(quiz);
        when(service.detail(ACCOUNT_ID, 501L)).thenReturn(new LmsQuizService.QuizDetail(quiz, List.of(link)));

        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUIZ_MANAGE"), routes().get(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalMarks").value(2.5))
                .andExpect(jsonPath("$.questions[0].position").value(1))
                .andExpect(jsonPath("$.questions[0].text").value("Which?"))
                .andExpect(jsonPath("$.questions[0].bankMarks").value(2.0))
                .andExpect(jsonPath("$.questions[0].options").doesNotExist());
        verify(service).create(eq(ACCOUNT_ID), eq(7L), any(LmsQuizSettings.class));
    }

    @Test
    void requestsAreCheckedBeforeTheService() throws Exception {
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUIZ_MANAGE");
        for (String bad : List.of(QUIZ.replace("\"timeLimitMinutes\":20", "\"timeLimitMinutes\":0"),
                QUIZ.replace("\"maxAttempts\":1", "\"maxAttempts\":101"),
                QUIZ.replace("\"negativeMarkPercent\":25", "\"negativeMarkPercent\":100.5"),
                QUIZ.replace("\"type\":\"GRADED\",", ""),
                QUIZ.replace("\"title\":\"Normalization check\"", "\"title\":\" \""),
                QUIZ.replace("\"dueAt\":\"2026-11-21T18:29:00Z\"", "\"dueAt\":\"2026-11-21T18:29:00\""))) {
            mockMvc.perform(as(manager, json(post("/api/v1/lms/faculty/courses/7/quizzes"), bad))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(as(manager, json(post("/api/v1/lms/faculty/quizzes/501/questions"), "{}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(manager, json(put("/api/v1/lms/faculty/quizzes/501/questions/601"), "{\"marks\":0}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(manager, json(put("/api/v1/lms/faculty/quizzes/501/question-order"), "{}"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
