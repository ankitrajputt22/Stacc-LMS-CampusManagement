package com.stacc.backend.lms.api;

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
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsOutcomeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Students read outcomes; faculty with the content manage permission add, correct, and archive them. */
class LmsOutcomeControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;

    @MockitoBean
    private LmsOutcomeService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static List<MockHttpServletRequestBuilder> changes() {
        return List.of(
                post("/api/v1/lms/faculty/courses/7/outcomes").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Analyse costs.\"}"),
                put("/api/v1/lms/faculty/outcomes/91").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Analyse costs.\"}"),
                post("/api/v1/lms/faculty/outcomes/91/archive"));
    }

    @Test
    void readingAndChangingNeedTheirOwnRules() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder change : changes()) {
                mockMvc.perform(as(wrong, change)).andExpect(status().isForbidden());
            }
        }
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), get("/api/v1/lms/courses/7/outcomes"))).andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/v1/lms/faculty/courses/7/outcomes"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);

        when(service.add(ACCOUNT_ID, 7L, "Analyse costs.")).thenReturn(new LmsCourseOutcome(ContentFixtures.lmsCourse(7), 1, "Analyse costs."));
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"), changes().getFirst()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CO1"));
        verify(service).add(ACCOUNT_ID, 7L, "Analyse costs.");
    }

    @Test
    void anAssignmentIsAlignedOnlyWithTheAssignmentManagePermission() throws Exception {
        MockHttpServletRequestBuilder align = put("/api/v1/lms/faculty/assignments/9/alignment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloomLevel\":\"APPLY\",\"outcomeIds\":[91]}");
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"), align)).andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"),
                put("/api/v1/lms/faculty/assignments/9/alignment").contentType(MediaType.APPLICATION_JSON).content("{\"bloomLevel\":\"SOMETHING\",\"outcomeIds\":[]}")))
                .andExpect(status().isBadRequest());
    }
}
