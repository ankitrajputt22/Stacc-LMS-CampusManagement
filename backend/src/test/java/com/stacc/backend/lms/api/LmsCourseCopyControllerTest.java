package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.copy.LmsCourseCopyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Copying needs the faculty role with the course view and content manage permissions, as the token's account. */
class LmsCourseCopyControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;

    @MockitoBean
    private LmsCourseCopyService copies;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static MockHttpServletRequestBuilder copy() {
        return post("/api/v1/lms/faculty/courses/8/copy").contentType(MediaType.APPLICATION_JSON).content("{\"sourceLmsCourseId\":7}");
    }

    @Test
    void onlyFacultyWhoManageContentMayListSourcesOrCopy() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token("ROLE_FACULTY", "LMS_CONTENT_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"))) {
            mockMvc.perform(as(wrong, get("/api/v1/lms/faculty/courses/8/copy-sources"))).andExpect(status().isForbidden());
            mockMvc.perform(as(wrong, copy())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(copies);

        when(copies.copy(ACCOUNT_ID, 8L, 7L)).thenReturn(new LmsCourseCopyService.CopyResult(1, 2, 0, true));
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"), copy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.syllabus").value(true));
        verify(copies).copy(ACCOUNT_ID, 8L, 7L);
    }

    @Test
    void aMissingSourceIsRefusedBeforeAnyCopy() throws Exception {
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                post("/api/v1/lms/faculty/courses/8/copy").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(copies);
    }
}
