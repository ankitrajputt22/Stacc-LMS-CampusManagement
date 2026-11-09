package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.question.QuestionType;
import com.stacc.backend.lms.quiz.LmsQuiz;
import com.stacc.backend.lms.quiz.LmsQuizQuestion;
import com.stacc.backend.lms.quiz.QuizFixtures;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttempt;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptService;
import com.stacc.backend.lms.quiz.attempt.QuizScoring;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only students with the quiz permission take quizzes, and an attempt never carries what gives its answers away. */
class LmsStudentQuizControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 31;
    private static final Instant NOW = Instant.parse("2026-11-10T05:00:00Z");

    @MockitoBean
    private LmsQuizAttemptService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(
                get("/api/v1/lms/courses/7/quizzes"),
                get("/api/v1/lms/courses/7/quizzes/501"),
                post("/api/v1/lms/courses/7/quizzes/501/attempts"),
                get("/api/v1/lms/quiz-attempts/9"),
                put("/api/v1/lms/quiz-attempts/9/answers/601").contentType(MediaType.APPLICATION_JSON).content("{\"selectedOptions\":[2]}"),
                post("/api/v1/lms/quiz-attempts/9/submit"));
    }

    @Test
    void everyRouteNeedsTheStudentRoleCourseViewAndTheQuizPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_ASSIGNMENT_SUBMIT"),
                token("ROLE_STUDENT", "LMS_QUIZ_TAKE"),
                token("ROLE_FACULTY", "LMS_COURSE_VIEW", "LMS_QUIZ_TAKE", "LMS_QUIZ_MANAGE"),
                token("ROLE_ADMIN", "LMS_COURSE_VIEW", "LMS_QUIZ_TAKE"))) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void anAttemptSendsChoicesWithoutTheirKeysAndNoShortAnswerOptions() throws Exception {
        LmsCourse course = SubmissionFixtures.course(7);
        LmsQuiz quiz = new LmsQuiz(course, 21L, QuizFixtures.graded("Unit test"));
        ReflectionTestUtils.setField(quiz, "id", 501L);
        quiz.publish(NOW);
        LmsQuestion single = new LmsQuestion(course, 21L, QuestionFixtures.singleChoice("Which?"));
        LmsQuestion trueFalse = new LmsQuestion(course, 21L, QuestionFixtures.of(QuestionType.TRUE_FALSE,
                List.of(), true, null));
        LmsQuizQuestion first = new LmsQuizQuestion(quiz, single, 1, new BigDecimal("2"));
        LmsQuizQuestion second = new LmsQuizQuestion(quiz, trueFalse, 2, BigDecimal.ONE);
        ReflectionTestUtils.setField(first, "id", 601L);
        ReflectionTestUtils.setField(second, "id", 602L);
        LmsQuizAttempt attempt = new LmsQuizAttempt(quiz, SubmissionFixtures.enrollment(course, ACCOUNT_ID, 41), 1, NOW);
        ReflectionTestUtils.setField(attempt, "id", 9L);
        when(service.attempt(ACCOUNT_ID, 9L)).thenReturn(new LmsQuizAttemptService.AttemptView(attempt, List.of(first, second), Map.of(),
                QuizScoring.score(quiz, List.of(first, second), Map.of(), Map.of()), NOW));

        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_QUIZ_TAKE"), get("/api/v1/lms/quiz-attempts/9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deadlineAt").value("2026-11-10T05:20:00Z"))
                .andExpect(jsonPath("$.questions[0].options.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].position").value(1))
                .andExpect(jsonPath("$.questions[1].options.length()").value(0))
                .andExpect(content().string(not(containsString("correct"))))
                .andExpect(content().string(not(containsString("trueFalseAnswer\":true"))))
                .andExpect(jsonPath("$.resultStatus").value("NOT_HANDED_IN"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(content().string(not(containsString("explanation\":\""))));
    }

    @Test
    void answersAreCheckedBeforeTheService() throws Exception {
        String student = token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_QUIZ_TAKE");
        for (String bad : List.of("{\"selectedOptions\":[1,2,3,4,5,6,7,8,9,10,11]}", "{\"selectedOptions\":[null]}",
                "{\"text\":\"" + "x".repeat(20_001) + "\"}")) {
            mockMvc.perform(as(student, put("/api/v1/lms/quiz-attempts/9/answers/601").contentType(MediaType.APPLICATION_JSON).content(bad)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.saveAnswer(eq(ACCOUNT_ID), eq(9L), eq(601L), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, LmsQuizAttemptService.TIME_UP));
        mockMvc.perform(as(student, put("/api/v1/lms/quiz-attempts/9/answers/601").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOptions\":[2]}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(LmsQuizAttemptService.TIME_UP));
    }
}
