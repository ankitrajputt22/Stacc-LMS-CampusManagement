package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty who manage assignments set submission rules; the body is checked before the service. */
class LmsAssignmentRulesControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private LmsAssignmentManagementService management;

    private static MockHttpServletRequestBuilder rules(String token, String body) {
        return put("/api/v1/lms/faculty/assignments/5/submission-rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 21L, authorities);
    }

    @Test
    void settingRulesNeedsTheFacultyRoleCourseViewAndAssignmentManagement() throws Exception {
        String body = "{\"allowLateSubmissions\":true,\"maxAttempts\":2}";
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_GRADE"),
                token("ROLE_FACULTY", "LMS_ASSIGNMENT_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE"))) {
            mockMvc.perform(rules(wrong, body)).andExpect(status().isForbidden());
        }
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_ASSIGNMENT_MANAGE");
        for (String bad : List.of("{}", "{\"allowLateSubmissions\":true,\"maxAttempts\":0}",
                "{\"allowLateSubmissions\":true,\"maxAttempts\":101}",
                "{\"allowLateSubmissions\":true,\"availableFrom\":\"2026-11-10T10:00:00\"}")) {
            mockMvc.perform(rules(manager, bad)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(management);
    }
}
