package com.stacc.backend.lms.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.announcement.LmsAnnouncementAttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty who may manage content add or remove announcement files; each side downloads through its own route. */
class LmsAnnouncementFilesControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private LmsAnnouncementAttachmentService files;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 21L, authorities);
    }

    @Test
    void changingFilesNeedsTheFacultyRoleAndContentPermission() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token("ROLE_STUDENT", "LMS_CONTENT_MANAGE", "LMS_COURSE_VIEW"),
                token("ROLE_ADMIN", "LMS_CONTENT_MANAGE"))) {
            mockMvc.perform(multipart("/api/v1/lms/faculty/announcements/5/files")
                    .file(new MockMultipartFile("files", "notes.pdf", "application/pdf", new byte[] {1}))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrong)).andExpect(status().isForbidden());
            mockMvc.perform(as(wrong, post("/api/v1/lms/faculty/announcements/5/files/9/remove"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(files);
    }

    @Test
    void eachSideDownloadsOnlyThroughItsOwnRoute() throws Exception {
        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/v1/lms/faculty/announcements/5/files/9")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), get("/api/v1/lms/courses/7/announcements/5/files/9")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_ADMIN"), get("/api/v1/lms/courses/7/announcements/5/files/9"))).andExpect(status().isForbidden());
        verifyNoInteractions(files);
    }
}
