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
 * Calls the faculty dashboard route through Spring Security. The query service is a stand-in, so these tests are
 * about who gets in and whose dashboard is asked for. What the dashboard contains is tested with the service.
 */
class FacultyDashboardControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/dashboard/faculty";
    private static final long ACCOUNT = 20L;

    @MockitoBean
    private FacultyDashboardQueryService dashboard;

    private static String token(String... authorities) {
        return "Bearer " + TestTokens.valid("EMP-TEST-20", ACCOUNT, authorities);
    }

    private static FacultyDashboardResponse empty() {
        return new FacultyDashboardResponse(0, List.of(), 0, List.of(), 0, List.of(), 0, List.of());
    }

    @Test
    void theDashboardNeedsTheFacultyRoleAndTheDashboardPermissionTogether() throws Exception {
        mockMvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        for (String denied : new String[] {
                token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE", "LMS_ASSIGNMENT_GRADE", "ATTENDANCE_MANAGE"),
                token("FACULTY_DASHBOARD_VIEW"),
                token("ROLE_STUDENT", "FACULTY_DASHBOARD_VIEW", "STUDENT_DASHBOARD_VIEW"),
                token("ROLE_ADMIN", "FACULTY_DASHBOARD_VIEW", "ACADEMIC_MASTER_VIEW")}) {
            mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, denied)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(dashboard);
    }

    @Test
    void theFacultyMemberComesFromTheTokenNeverFromTheRequest() throws Exception {
        when(dashboard.getDashboard(ACCOUNT)).thenReturn(empty());

        mockMvc.perform(get(ROUTE).param("facultyProfileId", "9").param("teachingAssignmentId", "3")
                        .header(HttpHeaders.AUTHORIZATION, token("ROLE_FACULTY", "FACULTY_DASHBOARD_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachingCourseCount").value(0))
                .andExpect(jsonPath("$.needsGrading").isArray())
                .andExpect(jsonPath("$.openAttendanceSessions").isArray());
        verify(dashboard).getDashboard(ACCOUNT);
    }

    @Test
    void aFacultyMemberWhoIsAlsoAStudentGetsTheFacultyDashboard() throws Exception {
        when(dashboard.getDashboard(ACCOUNT)).thenReturn(empty());

        mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION,
                        token("ROLE_STUDENT", "STUDENT_DASHBOARD_VIEW", "ROLE_FACULTY", "FACULTY_DASHBOARD_VIEW")))
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
                .andExpect(jsonPath("$.components.schemas.FacultyDashboardResponse.properties.needsGrading").exists())
                .andExpect(jsonPath("$.components.schemas.FacultyDashboardResponse.properties.classAverage").doesNotExist());
    }
}
