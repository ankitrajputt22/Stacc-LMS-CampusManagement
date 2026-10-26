package com.stacc.backend.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.report.AdminReportQueryService;
import com.stacc.backend.report.AdminReports.AdminCourseEnrollmentReportPage;
import com.stacc.backend.report.AdminReports.AdminReportFilters;
import com.stacc.backend.report.FacultyReportQueryService;
import com.stacc.backend.report.ReportActor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the report routes through Spring Security, with the report services as stand-ins. */
class ReportControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private FacultyReportQueryService faculty;

    @MockitoBean
    private AdminReportQueryService admin;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    // Fresh builders every time: a builder keeps the headers of earlier calls.
    private static List<MockHttpServletRequestBuilder> facultyRoutes() {
        return List.of(get("/api/reports/faculty/my-courses"), get("/api/reports/faculty/course-offerings/31"));
    }

    private static List<MockHttpServletRequestBuilder> adminRoutes() {
        return List.of(get("/api/reports/admin/overview"), get("/api/reports/admin/enrollments"),
                get("/api/reports/admin/enrollments/export.csv"));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    private void prepareAdmin() {
        when(admin.filters(any(), any(), any())).thenReturn(new AdminReportFilters(null, null, null));
        when(admin.enrollments(any(), anyInt(), anyInt())).thenReturn(new AdminCourseEnrollmentReportPage(List.of(), 0, 20, 0, 0));
        when(admin.enrollmentCsv(any())).thenReturn("\"Academic Session\"\r\n");
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder route : adminRoutes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(faculty, admin);
    }

    @Test
    void facultyReportsNeedTheFacultyRoleWithFacultyReportView() throws Exception {
        for (MockHttpServletRequestBuilder route : facultyRoutes()) {
            assertThat(statusOf(route, token(80, "ROLE_FACULTY", "FACULTY_REPORT_VIEW"))).isEqualTo(200);
        }
        for (String denied : new String[] {token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                token(1, "ROLE_ADMIN", "ADMIN_REPORT_VIEW", "FACULTY_REPORT_VIEW"), token(70, "ROLE_STUDENT", "FACULTY_REPORT_VIEW")}) {
            for (MockHttpServletRequestBuilder route : facultyRoutes()) {
                assertThat(statusOf(route, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void adminReportsNeedTheAdminRoleWithAdminReportView() throws Exception {
        prepareAdmin();
        for (MockHttpServletRequestBuilder route : adminRoutes()) {
            assertThat(statusOf(route, token(1, "ROLE_ADMIN", "ADMIN_REPORT_VIEW"))).isEqualTo(200);
        }
        for (String denied : new String[] {token(1, "ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW"),
                token(80, "ROLE_FACULTY", "FACULTY_REPORT_VIEW", "ADMIN_REPORT_VIEW"), token(70, "ROLE_STUDENT", "ADMIN_REPORT_VIEW")}) {
            for (MockHttpServletRequestBuilder route : adminRoutes()) {
                assertThat(statusOf(route, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void aFacultyAndAdminAccountGetsBothPerspectives() throws Exception {
        prepareAdmin();
        String both = token(2, "ROLE_FACULTY", "ROLE_ADMIN", "FACULTY_REPORT_VIEW", "ADMIN_REPORT_VIEW");
        assertThat(statusOf(get("/api/reports/faculty/my-courses"), both)).isEqualTo(200);
        assertThat(statusOf(get("/api/reports/admin/overview"), both)).isEqualTo(200);
    }

    @Test
    void theCallerAndFiltersComeFromTheTokenAndQuery() throws Exception {
        prepareAdmin();
        mockMvc.perform(get("/api/reports/faculty/course-offerings/31").param("facultyProfileId", "9")
                .header(HttpHeaders.AUTHORIZATION, token(80, "ROLE_FACULTY", "FACULTY_REPORT_VIEW"))).andExpect(status().isOk());
        verify(faculty).courseReport(80L, 31L);

        mockMvc.perform(get("/api/reports/admin/overview").param("academicSessionId", "2").param("programId", "4")
                .param("semesterId", "5").header(HttpHeaders.AUTHORIZATION, token(1, "ROLE_ADMIN", "ADMIN_REPORT_VIEW",
                        "HOSTEL_MANAGE"))).andExpect(status().isOk());
        verify(admin).filters(2L, 4L, 5L);
        ArgumentCaptor<ReportActor> actor = ArgumentCaptor.forClass(ReportActor.class);
        verify(admin).overview(actor.capture(), any());
        assertThat(actor.getValue().accountId()).isEqualTo(1L);
        assertThat(actor.getValue().roles()).containsExactly("ADMIN");
        assertThat(actor.getValue().authorities()).contains("ADMIN_REPORT_VIEW", "HOSTEL_MANAGE");

        mockMvc.perform(get("/api/reports/admin/enrollments").param("page", "2").param("size", "50")
                .header(HttpHeaders.AUTHORIZATION, token(1, "ROLE_ADMIN", "ADMIN_REPORT_VIEW"))).andExpect(status().isOk());
        verify(admin).enrollments(any(), eq(2), eq(50));
    }

    @Test
    void theCsvIsADownloadWithTheSameFilters() throws Exception {
        prepareAdmin();
        mockMvc.perform(get("/api/reports/admin/enrollments/export.csv").param("programId", "4")
                        .header(HttpHeaders.AUTHORIZATION, token(1, "ROLE_ADMIN", "ADMIN_REPORT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"stacc-enrollment-report.csv\""))
                .andExpect(content().string("\"Academic Session\"\r\n"));
        verify(admin).filters(null, 4L, null);
    }

    @Test
    void reportsAreReadOnlyAndInvalidIdsAre400() throws Exception {
        String both = token(2, "ROLE_FACULTY", "ROLE_ADMIN", "FACULTY_REPORT_VIEW", "ADMIN_REPORT_VIEW");
        for (MockHttpServletRequestBuilder request : List.of(post("/api/reports/admin/overview"),
                delete("/api/reports/faculty/course-offerings/31"), post("/api/reports/admin/enrollments"))) {
            assertThat(statusOf(request, both)).isIn(404, 405);
        }
        assertThat(statusOf(get("/api/reports/faculty/course-offerings/abc"), both)).isEqualTo(400);
        assertThat(statusOf(get("/api/reports/admin/enrollments").param("semesterId", "x"), both)).isEqualTo(400);
        verifyNoInteractions(faculty, admin);
    }

    @Test
    void theRoutesAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/reports/faculty/my-courses'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/reports/faculty/course-offerings/{courseOfferingId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/reports/admin/overview'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/reports/admin/enrollments'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/reports/admin/enrollments/export.csv'].get.responses['200']").exists());
    }
}
