package com.stacc.backend.lms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.syllabus.LmsSyllabus;
import com.stacc.backend.lms.syllabus.LmsSyllabusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Students read the published syllabus; faculty with the manage permission save and publish the draft. */
class LmsSyllabusControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;

    @MockitoBean
    private LmsSyllabusService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static MockHttpServletRequestBuilder save() {
        return put("/api/v1/lms/faculty/courses/7/syllabus").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"## Overview\"}");
    }

    @Test
    void studentsReadOnlyThePublishedVersion() throws Exception {
        LmsSyllabus syllabus = new LmsSyllabus(ContentFixtures.lmsCourse(7), "Published", Instant.parse("2026-11-01T00:00:00Z"));
        syllabus.publish(Instant.parse("2026-11-02T00:00:00Z"));
        syllabus.saveDraft("Draft only", Instant.parse("2026-11-03T00:00:00Z"));
        when(service.forStudent(ACCOUNT_ID, 7L)).thenReturn(Optional.of(syllabus));
        when(service.forStudent(ACCOUNT_ID, 8L)).thenReturn(Optional.empty());

        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/v1/lms/courses/7/syllabus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Published"))
                .andExpect(jsonPath("$.draftContent").doesNotExist());
        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW"), get("/api/v1/lms/courses/8/syllabus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void changesNeedTheFacultyRoleTheViewAndTheManagePermissions() throws Exception {
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token("ROLE_FACULTY", "LMS_CONTENT_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"))) {
            mockMvc.perform(as(wrong, save())).andExpect(status().isForbidden());
            mockMvc.perform(as(wrong, post("/api/v1/lms/faculty/courses/7/syllabus/publish"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(as(token("ROLE_FACULTY", "LMS_CONTENT_MANAGE"), get("/api/v1/lms/faculty/courses/7/syllabus")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), get("/api/v1/lms/courses/7/syllabus")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        when(service.saveDraft(any(), any(), any())).thenReturn(
                new LmsSyllabus(ContentFixtures.lmsCourse(7), "## Overview", Instant.parse("2026-11-01T00:00:00Z")));
        String manager = token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE");
        mockMvc.perform(as(manager, save()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftContent").value("## Overview"))
                .andExpect(jsonPath("$.unpublishedChanges").value(true));
        verify(service).saveDraft(ACCOUNT_ID, 7L, "## Overview");
    }

    @Test
    void anEmptyDraftIsRefusedBeforeAnyChange() throws Exception {
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                put("/api/v1/lms/faculty/courses/7/syllabus").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
