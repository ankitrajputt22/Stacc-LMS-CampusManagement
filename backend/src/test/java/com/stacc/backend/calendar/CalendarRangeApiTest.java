package com.stacc.backend.calendar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the calendar route with the real query services and empty stand-in repositories, so the range and time zone
 * rules answer through HTTP in the shared error format.
 */
class CalendarRangeApiTest extends DatabaseFreeApiTest {

    private static final String STUDENT = "Bearer " + TestTokens.valid("STU-TEST-1", 70, "ROLE_STUDENT", "ACADEMIC_CALENDAR_VIEW");

    private static MockHttpServletRequestBuilder calendar(String from, String to, String timeZone) {
        return get("/api/calendar/me").param("from", from).param("to", to).param("timeZone", timeZone)
                .header(HttpHeaders.AUTHORIZATION, STUDENT);
    }

    @Test
    void invalidRangesAndTimeZonesAnswer400WithASafeMessage() throws Exception {
        mockMvc.perform(calendar("2026-10-31", "2026-10-01", "Asia/Kolkata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AcademicCalendarQueryService.RANGE_ORDER));
        mockMvc.perform(calendar("2026-01-01", "2027-01-02", "Asia/Kolkata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AcademicCalendarQueryService.RANGE_TOO_LONG));
        mockMvc.perform(calendar("2026-10-01", "2026-10-31", "Asia/Nowhere"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AcademicCalendarQueryService.TIME_ZONE_INVALID));
    }

    @Test
    void aDayAMonthAndAYearOfEmptyDataAreEmptyCalendars() throws Exception {
        mockMvc.perform(calendar("2026-10-20", "2026-10-20", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromDate").value("2026-10-20"))
                .andExpect(jsonPath("$.toDate").value("2026-10-20"));
        mockMvc.perform(calendar("2026-10-01", "2026-10-31", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicEvents").isEmpty())
                .andExpect(jsonPath("$.studentAssignmentDeadlines").isEmpty())
                .andExpect(jsonPath("$.facultyAssignmentDeadlines").isEmpty());
        mockMvc.perform(calendar("2026-01-01", "2027-01-01", "America/New_York")).andExpect(status().isOk());
    }
}
