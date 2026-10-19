package com.stacc.backend.dashboard;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Calls the student dashboard route through Spring Security. The query service is a stand-in, so these tests are
 * about who gets in and whose dashboard is asked for. What the dashboard contains is tested with the service.
 */
class StudentDashboardControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/dashboard/student";
    private static final long ACCOUNT = 50L;

    @MockitoBean
    private StudentDashboardQueryService dashboard;

    private static String token(String... authorities) {
        return "Bearer " + TestTokens.valid("STU-TEST-50", ACCOUNT, authorities);
    }

    private static StudentDashboardResponse empty() {
        return new StudentDashboardResponse(0, List.of(), 0, List.of(), List.of(), 0, List.of(), 0, List.of());
    }

    @Test
    void theDashboardNeedsTheStudentRoleAndTheDashboardPermissionTogether() throws Exception {
        mockMvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        for (String denied : new String[] {
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "ATTENDANCE_VIEW", "ACADEMIC_RESULT_VIEW"),
                token("STUDENT_DASHBOARD_VIEW"),
                token("ROLE_FACULTY", "STUDENT_DASHBOARD_VIEW", "FACULTY_COURSE_VIEW"),
                token("ROLE_ADMIN", "STUDENT_DASHBOARD_VIEW", "ACADEMIC_MASTER_VIEW")}) {
            mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, denied)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(dashboard);
    }

    @Test
    void theStudentComesFromTheTokenNeverFromTheRequest() throws Exception {
        when(dashboard.getDashboard(ACCOUNT)).thenReturn(empty());

        mockMvc.perform(get(ROUTE).param("studentProfileId", "9").param("userAccountId", "77")
                        .header(HttpHeaders.AUTHORIZATION, token("ROLE_STUDENT", "STUDENT_DASHBOARD_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCount").value(0))
                .andExpect(jsonPath("$.courses").isArray())
                .andExpect(jsonPath("$.recentResults").isArray());
        verify(dashboard).getDashboard(ACCOUNT);
    }

    @Test
    void aStudentWhoIsAlsoFacultyGetsTheStudentDashboard() throws Exception {
        when(dashboard.getDashboard(ACCOUNT)).thenReturn(empty());

        mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION,
                        token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "ROLE_STUDENT", "STUDENT_DASHBOARD_VIEW")))
                .andExpect(status().isOk());
    }

    @Test
    void theRouteIsDocumentedWithTheBearerSchemeAndNoParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StudentDashboardResponse.properties.upcomingAssignments").exists())
                .andExpect(jsonPath("$.components.schemas.StudentDashboardResponse.properties.overallAttendancePercentage").doesNotExist());
    }
}
