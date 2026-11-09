package com.stacc.backend.lms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionBankService;
import com.stacc.backend.lms.question.QuestionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty with the question bank permission read or change the bank; students have no route to it. */
class LmsQuestionBankControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final String QUESTION = """
            {"type":"SINGLE_CHOICE","text":"Which normal form removes partial dependencies?","textFormat":"FORMATTED",
             "options":[{"text":"1NF","correct":false},{"text":"2NF","correct":true}],
             "topic":"Normalization","difficulty":"MEDIUM","marks":2}
            """;

    @MockitoBean
    private LmsQuestionBankService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(
                get("/api/v1/lms/faculty/courses/7/questions"),
                post("/api/v1/lms/faculty/courses/7/questions").contentType(MediaType.APPLICATION_JSON).content(QUESTION),
                put("/api/v1/lms/faculty/questions/301").contentType(MediaType.APPLICATION_JSON).content(QUESTION),
                post("/api/v1/lms/faculty/questions/301/review"),
                put("/api/v1/lms/faculty/questions/301/practice").contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}"),
                post("/api/v1/lms/faculty/questions/301/archive"));
    }

    @Test
    void everyRouteNeedsTheFacultyRoleCourseViewAndTheBankPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE", "LMS_ASSIGNMENT_MANAGE"),
                token("ROLE_FACULTY", "LMS_QUESTION_BANK_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/v1/lms/courses/7/questions"))).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void aQuestionIsSentWithItsAnswersToTheTeacher() throws Exception {
        LmsQuestion question = new LmsQuestion(ContentFixtures.lmsCourse(7), ACCOUNT_ID, QuestionFixtures.singleChoice("Which?"));
        when(service.add(eq(ACCOUNT_ID), eq(7L), any(), isNull())).thenReturn(question);

        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE"), routes().get(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.options[1].correct").value(true))
                .andExpect(jsonPath("$.reviewStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.practiceAllowed").value(false))
                .andExpect(jsonPath("$.marks").value(2.00));
        verify(service).add(eq(ACCOUNT_ID), eq(7L), any(), isNull());
    }

    @Test
    void requestsAreCheckedBeforeTheService() throws Exception {
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE");
        for (String bad : List.of(QUESTION.replace("\"marks\":2", "\"marks\":0"), QUESTION.replace("\"difficulty\":\"MEDIUM\",", ""),
                QUESTION.replace("\"type\":\"SINGLE_CHOICE\"", "\"type\":\"ESSAY\""), QUESTION.replace("\"text\":\"1NF\"", "\"text\":\" \""))) {
            mockMvc.perform(as(manager, post("/api/v1/lms/faculty/courses/7/questions").contentType(MediaType.APPLICATION_JSON).content(bad)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(as(manager, put("/api/v1/lms/faculty/questions/301/practice").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
