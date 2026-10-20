package com.stacc.backend.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the calendar route through Spring Security, with the query service as a stand-in. */
class CalendarControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/calendar/me";
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @MockitoBean
    private AcademicCalendarQueryService calendar;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static MockHttpServletRequestBuilder october() {
        return get(ROUTE).param("from", "2026-10-01").param("to", "2026-10-31").param("timeZone", "Asia/Kolkata");
    }

    private CalendarViewer viewerOf(String token) throws Exception {
        when(calendar.getMyCalendar(any(), any(), any(), any()))
                .thenReturn(new MyCalendarResponse(FROM, TO, "Asia/Kolkata", List.of(), List.of(), List.of()));
        mockMvc.perform(october().header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        ArgumentCaptor<CalendarViewer> viewer = ArgumentCaptor.forClass(CalendarViewer.class);
        verify(calendar, atLeastOnce()).getMyCalendar(viewer.capture(), eq(FROM), eq(TO), eq("Asia/Kolkata"));
        return viewer.getValue();
    }

    @Test
    void theCalendarNeedsAKnownRoleAndTheViewPermissionTogether() throws Exception {
        mockMvc.perform(october()).andExpect(status().isUnauthorized());
        for (String denied : new String[] {
                token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW", "STUDENT_DASHBOARD_VIEW"),
                token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                token(1, "ROLE_ADMIN", "ACADEMIC_CALENDAR_MANAGE"),
                token(5, "ACADEMIC_CALENDAR_VIEW")}) {
            mockMvc.perform(october().header(HttpHeaders.AUTHORIZATION, denied)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(calendar);
    }

    @Test
    void eachRoleWithTheViewPermissionGetsItsOwnViewer() throws Exception {
        assertThat(viewerOf(token(70, "ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW")))
                .isEqualTo(new CalendarViewer(70L, true, false, false));
        assertThat(viewerOf(token(80, "ROLE_FACULTY", "ACADEMIC_CALENDAR_VIEW")))
                .isEqualTo(new CalendarViewer(80L, false, true, false));
        assertThat(viewerOf(token(1, "ROLE_ADMIN", "ACADEMIC_CALENDAR_VIEW")))
                .isEqualTo(new CalendarViewer(1L, false, false, true));
    }

    @Test
    void anAccountWithSeveralRolesGetsAllOfThemWhateverTheirOrder() throws Exception {
        assertThat(viewerOf(token(90, "ROLE_FACULTY", "ACADEMIC_CALENDAR_VIEW", "ROLE_STUDENT")))
                .isEqualTo(new CalendarViewer(90L, true, true, false));
    }

    @Test
    void theAccountComesFromTheTokenAndNeverFromTheRequest() throws Exception {
        when(calendar.getMyCalendar(any(), any(), any(), any()))
                .thenReturn(new MyCalendarResponse(FROM, TO, "Asia/Kolkata", List.of(), List.of(), List.of()));

        mockMvc.perform(october().param("userAccountId", "99").param("studentProfileId", "57")
                        .header(HttpHeaders.AUTHORIZATION, token(70, "ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.academicEvents").isEmpty())
                .andExpect(jsonPath("$.facultyAssignmentDeadlines").isEmpty());

        verify(calendar).getMyCalendar(new CalendarViewer(70L, true, false, false), FROM, TO, "Asia/Kolkata");
    }

    @Test
    void missingOrMalformedParametersAreRefusedBeforeTheService() throws Exception {
        String student = token(70, "ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW");
        for (MockHttpServletRequestBuilder bad : new MockHttpServletRequestBuilder[] {
                get(ROUTE).param("from", "2026-10-01").param("to", "2026-10-31"),
                get(ROUTE).param("to", "2026-10-31").param("timeZone", "UTC"),
                get(ROUTE).param("from", "01/10/2026").param("to", "2026-10-31").param("timeZone", "UTC"),
                get(ROUTE).param("from", "2026-10-01").param("to", "2026-10-31T00:00").param("timeZone", "UTC")}) {
            mockMvc.perform(bad.header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(ROUTE).header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(calendar);
    }

    @Test
    void theRouteIsDocumentedWithItsThreeParametersOnly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.parameters.length()").value(3))
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.parameters[*].name")
                        .value(containsInAnyOrder("from", "to", "timeZone")))
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['403']").exists())
                .andExpect(jsonPath("$.components.schemas.MyCalendarResponse.properties.studentAssignmentDeadlines").exists());
    }
}
