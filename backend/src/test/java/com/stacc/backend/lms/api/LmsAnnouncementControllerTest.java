package com.stacc.backend.lms.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementManagementService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the announcement routes through Spring Security. The services are stand-ins, so these tests are
 * about who gets in, what is checked at the door, and what is sent back.
 */
class LmsAnnouncementControllerTest extends DatabaseFreeApiTest {

    private static final long ACCOUNT_ID = 21;
    private static final String VALID = "{\"title\":\"Exam schedule\",\"message\":\"Monday at 9.\\nRoom 4.\"}";

    @MockitoBean
    private LmsAnnouncementQueryService queryService;

    @MockitoBean
    private LmsAnnouncementManagementService managementService;

    private final LmsAnnouncement announcement = announcement();

    private static LmsAnnouncement announcement() {
        LmsAnnouncement created = new LmsAnnouncement(ContentFixtures.lmsCourse(7), "Exam schedule", "Monday at 9.\nRoom 4.");
        ReflectionTestUtils.setField(created, "id", 5L);
        return created;
    }

    private static String tokenWith(String... authorities) {
        return TestTokens.valid("TEST-USER-1", ACCOUNT_ID, authorities);
    }

    private static String studentToken() {
        return tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW");
    }

    private static String managerToken() {
        return tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> changeRoutes() {
        return List.of(
                json(post("/api/lms/faculty/courses/7/announcements"), VALID),
                json(put("/api/lms/faculty/announcements/5"), VALID),
                post("/api/lms/faculty/announcements/5/publish"),
                post("/api/lms/faculty/announcements/5/archive"));
    }

    @Test
    void studentGetsPublishedAnnouncementsWithoutStatus() throws Exception {
        when(queryService.getPublishedAnnouncements(ACCOUNT_ID, 7L)).thenReturn(List.of(new LmsCourseAnnouncementResponse(
                5L, "Exam schedule", "Monday at 9.\nRoom 4.", Instant.parse("2026-10-14T09:30:00Z"))));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/announcements").param("accountId", "999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[0].message").value("Monday at 9.\nRoom 4."))
                .andExpect(jsonPath("$[0].publishedAt").value("2026-10-14T09:30:00Z"))
                .andExpect(content().string(not(containsString("status"))));

        verify(queryService).getPublishedAnnouncements(ACCOUNT_ID, 7L);
    }

    @Test
    void studentRouteNeedsTheStudentRoleAndPermission() throws Exception {
        mockMvc.perform(get("/api/lms/courses/7/announcements")).andExpect(status().isUnauthorized());
        for (String token : List.of(tokenWith("ROLE_STUDENT"), tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                managerToken(), tokenWith("ROLE_ADMIN", "LMS_COURSE_VIEW"))) {
            mockMvc.perform(as(token, get("/api/lms/courses/7/announcements"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(queryService);
    }

    @Test
    void unavailableCourseKeepsTheSafeMessage() throws Exception {
        when(queryService.getPublishedAnnouncements(ACCOUNT_ID, 7L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Course not found or unavailable."));

        mockMvc.perform(as(studentToken(), get("/api/lms/courses/7/announcements")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Course not found or unavailable."));
    }

    @Test
    void facultyListNeedsTheFacultyRoleAndViewPermission() throws Exception {
        when(queryService.getManagedAnnouncements(ACCOUNT_ID, 7L)).thenReturn(List.of(LmsFacultyAnnouncementResponse.of(announcement)));

        mockMvc.perform(as(tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"), get("/api/lms/faculty/courses/7/announcements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].publishedAt").doesNotExist())
                .andExpect(jsonPath("$[0].*", hasSize(7)));
        mockMvc.perform(get("/api/lms/faculty/courses/7/announcements")).andExpect(status().isUnauthorized());
        for (String token : List.of(tokenWith("ROLE_FACULTY"), tokenWith("ROLE_FACULTY", "LMS_CONTENT_MANAGE"), studentToken(),
                tokenWith("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE"))) {
            mockMvc.perform(as(token, get("/api/lms/faculty/courses/7/announcements"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void addingAnAnnouncementAnswersCreatedAndIgnoresStatusAndCourseInTheBody() throws Exception {
        when(managementService.create(ACCOUNT_ID, 7L, "Exam schedule", "Monday at 9.\nRoom 4.")).thenReturn(announcement);

        mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/announcements"),
                        "{\"title\":\"Exam schedule\",\"message\":\"Monday at 9.\\nRoom 4.\",\"status\":\"PUBLISHED\","
                                + "\"publishedAt\":\"2020-01-01T00:00:00Z\",\"lmsCourseId\":99,\"facultyProfileId\":3}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(managementService).create(ACCOUNT_ID, 7L, "Exam schedule", "Monday at 9.\nRoom 4.");
    }

    @Test
    void eachChangeRouteCallsItsActionForTheAccountInTheToken() throws Exception {
        when(managementService.update(ACCOUNT_ID, 5L, "Exam schedule", "Monday at 9.\nRoom 4.")).thenReturn(announcement);
        when(managementService.publish(ACCOUNT_ID, 5L)).thenReturn(announcement);
        when(managementService.archive(ACCOUNT_ID, 5L)).thenReturn(announcement);

        for (MockHttpServletRequestBuilder route : changeRoutes().subList(1, 4)) {
            mockMvc.perform(as(managerToken(), route)).andExpect(status().isOk());
        }

        verify(managementService).update(ACCOUNT_ID, 5L, "Exam schedule", "Monday at 9.\nRoom 4.");
        verify(managementService).publish(ACCOUNT_ID, 5L);
        verify(managementService).archive(ACCOUNT_ID, 5L);
    }

    @Test
    void changesNeedTheFacultyRoleAndTheExistingContentPermission() throws Exception {
        for (MockHttpServletRequestBuilder route : changeRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String token : List.of(
                tokenWith("ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                tokenWith("LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_ADMIN", "LMS_CONTENT_MANAGE"),
                tokenWith("ROLE_FACULTY", "LMS_ANNOUNCEMENT_MANAGE"))) {
            for (MockHttpServletRequestBuilder route : changeRoutes()) {
                mockMvc.perform(as(token, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void invalidBodiesAreRefusedBeforeAnyChange() throws Exception {
        for (String body : List.of(
                "{\"message\":\"Text\"}",
                "{\"title\":\"Title\"}",
                "{\"title\":\" \",\"message\":\"Text\"}",
                "{\"title\":\"Title\",\"message\":\"  \"}",
                "{\"title\":\"" + "x".repeat(201) + "\",\"message\":\"Text\"}",
                "{\"title\":\"Title\",\"message\":\"" + "x".repeat(10_001) + "\"}")) {
            mockMvc.perform(as(managerToken(), json(post("/api/lms/faculty/courses/7/announcements"), body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
            mockMvc.perform(as(managerToken(), json(put("/api/lms/faculty/announcements/5"), body)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(managementService);
    }

    @Test
    void refusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(managementService.publish(anyLong(), eq(6L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Announcement not found or unavailable."));
        when(managementService.publish(anyLong(), eq(5L)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This announcement is archived and can no longer be changed."));

        mockMvc.perform(as(managerToken(), post("/api/lms/faculty/announcements/6/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Announcement not found or unavailable."));
        mockMvc.perform(as(managerToken(), post("/api/lms/faculty/announcements/5/publish")))
                .andExpect(status().isConflict());
    }

    @Test
    void announcementsCannotBeDeleted() throws Exception {
        mockMvc.perform(as(managerToken(), delete("/api/lms/faculty/announcements/5"))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(managementService);
    }

    @Test
    void announcementRoutesAppearInTheApiDocumentation() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : new String[][] {
                {"/api/lms/courses/{lmsCourseId}/announcements", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/announcements", "get", "200"},
                {"/api/lms/faculty/courses/{lmsCourseId}/announcements", "post", "201"},
                {"/api/lms/faculty/announcements/{announcementId}", "put", "200"},
                {"/api/lms/faculty/announcements/{announcementId}/publish", "post", "200"},
                {"/api/lms/faculty/announcements/{announcementId}/archive", "post", "200"}}) {
            String path = "$.paths['" + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['" + operation[2] + "']").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists())
                    .andExpect(jsonPath(path + ".responses['404']").exists());
        }
        result.andExpect(jsonPath("$.paths['/api/lms/faculty/courses/{lmsCourseId}/announcements'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/lms/faculty/announcements/{announcementId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LmsCourseAnnouncementResponse.properties.status").doesNotExist());
    }
}
