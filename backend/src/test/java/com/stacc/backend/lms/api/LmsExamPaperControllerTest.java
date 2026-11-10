package com.stacc.backend.lms.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.paper.ExamPaperDocument;
import com.stacc.backend.lms.paper.LmsExamPaper;
import com.stacc.backend.lms.paper.LmsExamPaperService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty with the exam paper permission reach blueprints and papers; requests are checked before the service. */
class LmsExamPaperControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final String BLUEPRINT = """
            {"title":"Mid-term","rows":[{"sectionTitle":"Section A","questionType":"SINGLE_CHOICE","questionCount":5,"marksEach":2}]}
            """;

    @MockitoBean
    private LmsExamPaperService service;

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
                get("/api/v1/lms/faculty/courses/7/exam-blueprints"),
                json(post("/api/v1/lms/faculty/courses/7/exam-blueprints"), BLUEPRINT),
                json(put("/api/v1/lms/faculty/exam-blueprints/3"), BLUEPRINT),
                post("/api/v1/lms/faculty/exam-blueprints/3/archive"),
                json(post("/api/v1/lms/faculty/exam-blueprints/3/papers"), "{}"),
                get("/api/v1/lms/faculty/courses/7/exam-papers"),
                json(post("/api/v1/lms/faculty/courses/7/exam-papers"), "{\"title\":\"End term\"}"),
                get("/api/v1/lms/faculty/exam-papers/9"),
                json(put("/api/v1/lms/faculty/exam-papers/9"), "{\"title\":\"End term\"}"),
                json(put("/api/v1/lms/faculty/exam-papers/9/questions"), "{\"questions\":[]}"),
                post("/api/v1/lms/faculty/exam-papers/9/finalize"),
                post("/api/v1/lms/faculty/exam-papers/9/archive"),
                get("/api/v1/lms/faculty/exam-papers/9/document"));
    }

    @Test
    void everyRouteNeedsTheFacultyRoleCourseViewAndThePaperPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_QUESTION_BANK_MANAGE", "LMS_QUIZ_MANAGE"),
                token("ROLE_FACULTY", "LMS_EXAM_PAPER_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_EXAM_PAPER_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_EXAM_PAPER_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : routes()) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void aBlueprintRowNeedsACountAndMarksBeforeTheService() throws Exception {
        String allowed = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_EXAM_PAPER_MANAGE");
        mockMvc.perform(as(allowed, json(post("/api/v1/lms/faculty/courses/7/exam-blueprints"),
                "{\"title\":\"Mid-term\",\"rows\":[{\"questionCount\":0}]}"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(allowed, json(post("/api/v1/lms/faculty/courses/7/exam-blueprints"),
                "{\"title\":\"Mid-term\",\"rows\":[]}"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void theWordDocumentIsAnAttachmentThatIsNeverCached() throws Exception {
        LmsExamPaper paper = new LmsExamPaper(ContentFixtures.lmsCourse(7), ACCOUNT_ID, null, "End term", null, null);
        when(service.paper(eq(ACCOUNT_ID), eq(9L)))
                .thenReturn(new LmsExamPaperService.PaperDetail(paper, "TST301", "Test Data Structures", List.of()));
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_EXAM_PAPER_MANAGE"),
                        get("/api/v1/lms/faculty/exam-papers/9/document").param("answers", "true")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, ExamPaperDocument.CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"exam-paper-9-with-answers.docx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }
}
