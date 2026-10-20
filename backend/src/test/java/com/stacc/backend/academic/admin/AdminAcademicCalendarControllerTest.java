package com.stacc.backend.academic.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventCreate;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventUpdate;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the admin calendar routes through Spring Security. The service is a stand-in, so these tests are about who
 * gets in, what reaches the service, and what is sent back.
 */
class AdminAcademicCalendarControllerTest extends DatabaseFreeApiTest {

    private static final String EVENTS = "/api/admin/academic/calendar/events";
    private static final String BODY = """
            {"academicSessionId":2,"semesterId":9,"title":"Mid-semester examination week","description":null,
             "category":"EXAM","startDate":"2026-10-20","endDate":"2026-10-25"}""";

    @MockitoBean
    private AcademicCalendarManagementService calendar;

    private static String token(String... authorities) {
        return TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static String manager() {
        return token("ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> reads() {
        return List.of(get(EVENTS), get(EVENTS + "/14"));
    }

    private static List<MockHttpServletRequestBuilder> changes() {
        return List.of(json(post(EVENTS), BODY), json(put(EVENTS + "/14"), BODY), post(EVENTS + "/14/publish"),
                post(EVENTS + "/14/cancel"));
    }

    // Each call gets new requests, so a token is never added to a request that already carries one.
    private void refused(List<String> tokens, Supplier<List<MockHttpServletRequestBuilder>> requests) throws Exception {
        for (String denied : tokens) {
            for (MockHttpServletRequestBuilder request : requests.get()) {
                mockMvc.perform(as(denied, request)).andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder request : requests.get()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(calendar);
    }

    @Test
    void readingNeedsTheAdminRoleAndTheCalendarViewPermissionTogether() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_CALENDAR_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_OFFERING_VIEW", "ADMIN_DASHBOARD_VIEW"),
                token("ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE")),
                AdminAcademicCalendarControllerTest::reads);

        for (MockHttpServletRequestBuilder read : reads()) {
            mockMvc.perform(as(token("ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW"), read)).andExpect(status().isOk());
        }
    }

    @Test
    void changingNeedsTheManagePermissionToo() throws Exception {
        refused(List.of(
                token("ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW"),
                token("ROLE_ADMIN", "ACADEMIC_CALENDAR_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_MASTER_MANAGE", "ACADEMIC_OFFERING_MANAGE"),
                token("ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE"),
                token("ROLE_FACULTY", "ACADEMIC_CALENDAR_VIEW", "ACADEMIC_CALENDAR_MANAGE")),
                AdminAcademicCalendarControllerTest::changes);
    }

    @Test
    void aManagerCanCreateEditPublishAndCancel() throws Exception {
        mockMvc.perform(as(manager(), json(post(EVENTS), BODY))).andExpect(status().isCreated());
        mockMvc.perform(as(manager(), json(put(EVENTS + "/14"), BODY))).andExpect(status().isOk());
        mockMvc.perform(as(manager(), post(EVENTS + "/14/publish"))).andExpect(status().isOk());
        mockMvc.perform(as(manager(), post(EVENTS + "/14/cancel"))).andExpect(status().isOk());

        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 25);
        verify(calendar).create(new AcademicCalendarEventCreate(2L, 9L, "Mid-semester examination week", null,
                AcademicCalendarEventCategory.EXAM, start, end));
        verify(calendar).update(14L, new AcademicCalendarEventUpdate(2L, 9L, "Mid-semester examination week", null,
                AcademicCalendarEventCategory.EXAM, start, end));
        verify(calendar).publish(14L);
        verify(calendar).cancel(14L);
    }

    @Test
    void aStatusOrPublicationTimeInTheBodyNeverReachesTheService() throws Exception {
        mockMvc.perform(as(manager(), json(post(EVENTS), """
                        {"academicSessionId":2,"title":"Holiday","category":"HOLIDAY","startDate":"2026-10-20",
                         "endDate":"2026-10-20","status":"PUBLISHED","publishedAt":"2026-01-01T00:00:00Z","eventId":7}""")))
                .andExpect(status().isCreated());

        verify(calendar).create(new AcademicCalendarEventCreate(2L, null, "Holiday", null,
                AcademicCalendarEventCategory.HOLIDAY, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20)));
    }

    @Test
    void invalidBodiesAreRefusedWithFieldErrorsBeforeTheService() throws Exception {
        mockMvc.perform(as(manager(), json(post(EVENTS), """
                        {"academicSessionId":2,"title":"   ","startDate":"2026-10-20"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'category')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'endDate')]").exists());
        mockMvc.perform(as(manager(), json(post(EVENTS), BODY.replace("EXAM", "ASSIGNMENT"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(manager(), json(post(EVENTS), BODY.replace("2026-10-25", "25/10/2026"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(calendar);
    }

    @Test
    void theListPassesItsFilters() throws Exception {
        when(calendar.list(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 2L, 9L, AcademicCalendarEventStatus.CANCELLED))
                .thenReturn(List.of());

        mockMvc.perform(as(token("ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW"), get(EVENTS)
                        .param("from", "2026-10-01").param("to", "2026-10-31").param("academicSessionId", "2")
                        .param("semesterId", "9").param("status", "CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(as(token("ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW"), get(EVENTS).param("status", "ARCHIVED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoDeleteRoute() throws Exception {
        mockMvc.perform(as(manager(), delete(EVENTS + "/14"))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(calendar);
    }

    @Test
    void theRoutesAreDocumentedWithTheBearerSchemeAndNoDelete() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + EVENTS + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + EVENTS + "'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + EVENTS + "/{eventId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + EVENTS + "/{eventId}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['" + EVENTS + "/{eventId}/publish'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + EVENTS + "/{eventId}/cancel'].post.responses['200']").exists())
                .andExpect(jsonPath("$.components.schemas.AcademicCalendarEventCreate.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminCalendarEventResponse.properties.publishedAt").exists());
    }
}
