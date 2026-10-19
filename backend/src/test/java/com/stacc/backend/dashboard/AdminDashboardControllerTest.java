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

/** Calls the admin dashboard route through Spring Security, with the query service as a stand-in. */
class AdminDashboardControllerTest extends DatabaseFreeApiTest {

    private static final String ROUTE = "/api/dashboard/admin";

    @MockitoBean
    private AdminDashboardQueryService dashboard;

    private static String token(String... authorities) {
        return "Bearer " + TestTokens.valid("ADM-TEST-1", 1, authorities);
    }

    private static AdminDashboardResponse zero() {
        return new AdminDashboardResponse(new AdminDashboardResponse.AcademicMasters(0, 0, 0, 0, 0),
                new AdminDashboardResponse.AcademicOperations(0, 0, 0), List.of());
    }

    @Test
    void theDashboardNeedsTheAdminRoleAndTheDashboardPermissionTogether() throws Exception {
        mockMvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        for (String denied : new String[] {
                token("ROLE_ADMIN", "ACADEMIC_MASTER_VIEW", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_ENROLLMENT_VIEW"),
                token("ADMIN_DASHBOARD_VIEW"),
                token("ROLE_STUDENT", "ADMIN_DASHBOARD_VIEW", "STUDENT_DASHBOARD_VIEW"),
                token("ROLE_FACULTY", "ADMIN_DASHBOARD_VIEW", "FACULTY_DASHBOARD_VIEW")}) {
            mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, denied)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(dashboard);
    }

    @Test
    void anAdminWithThePermissionGetsZeroCountsAsNumbers() throws Exception {
        when(dashboard.getDashboard()).thenReturn(zero());

        mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION, token("ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicMasters.departmentCount").value(0))
                .andExpect(jsonPath("$.academicOperations.activeCourseEnrollmentCount").value(0))
                .andExpect(jsonPath("$.courseOfferings").isEmpty());
        verify(dashboard).getDashboard();
    }

    @Test
    void anAdminWhoAlsoHasOtherRolesGetsTheAdminDashboard() throws Exception {
        when(dashboard.getDashboard()).thenReturn(zero());

        mockMvc.perform(get(ROUTE).header(HttpHeaders.AUTHORIZATION,
                        token("ROLE_STUDENT", "ROLE_FACULTY", "ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW")))
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
                .andExpect(jsonPath("$.paths['" + ROUTE + "'].post").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminDashboardResponse.properties.academicMasters").exists());
    }
}
