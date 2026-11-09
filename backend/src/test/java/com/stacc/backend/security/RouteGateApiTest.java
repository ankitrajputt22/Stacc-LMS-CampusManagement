package com.stacc.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The route rules of the v0.8 to v0.16 areas, through Spring Security: nobody is let in without a session, and a role
 * without the area's permission is refused before any service runs. Per-record checks are tested in each service.
 */
class RouteGateApiTest extends DatabaseFreeApiTest {

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-GATE", 9001L, authorities);
    }

    @Test
    void protectedAreasNeedASession() throws Exception {
        for (String path : new String[] {"/api/v1/me/todo", "/api/v1/engagement/groups", "/api/v1/career/profile",
                "/api/v1/analytics/student/me", "/api/v1/exports", "/api/v1/hostel/student/outings"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aStudentIsRefusedStaffAndManagerRoutes() throws Exception {
        String student = bearer("ROLE_STUDENT", "LMS_COURSE_VIEW", "CAREER_USE", "CAMPUS_ENGAGEMENT_USE", "HOSTEL_OUTING_REQUEST");
        mockMvc.perform(post("/api/v1/gate/check").header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ABCDEFGHJKLMNPQR\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/career/manage/opportunities").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exports").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/hostel/warden/overdue").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/id-cards/check").header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ABCDEFGHJKLMNPQR\"}")).andExpect(status().isForbidden());
    }

    @Test
    void aRoleWithoutTheAreasPermissionIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/career/profile").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/faculty/courses/1").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY", "FACULTY_COURSE_VIEW")))
                .andExpect(status().isForbidden());
    }
}
