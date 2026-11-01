package com.stacc.backend.academic.profile;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.admin.AcademicProfileAdminService;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Academic profile routes: own profiles from the token, admin routes with the admin profile permissions. */
class MyAcademicProfileControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private MyAcademicProfileService mine;

    @MockitoBean
    private AcademicProfileAdminService admin;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("someone", 70, authorities);
    }

    @Test
    void myProfileNeedsTheOwnPermissionWithAStudentOrFacultyRole() throws Exception {
        when(mine.profile(anyLong(), anyBoolean(), anyBoolean())).thenReturn(new MyAcademicProfileResponse(null, null));
        mockMvc.perform(get("/api/v1/academic/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/academic/me").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/academic/me").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "ACADEMIC_PROFILE_VIEW_OWN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mine);

        mockMvc.perform(get("/api/v1/academic/me")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT", "ROLE_FACULTY", "ACADEMIC_PROFILE_VIEW_OWN")))
                .andExpect(status().isOk());
        verify(mine).profile(eq(70L), eq(true), eq(true));
    }

    @Test
    void adminProfileRoutesNeedTheAdminProfilePermissions() throws Exception {
        String viewer = bearer("ROLE_ADMIN", "ACADEMIC_PROFILE_VIEW");
        mockMvc.perform(get("/api/v1/admin/academic/profiles/students").header(HttpHeaders.AUTHORIZATION,
                bearer("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_VIEW"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/academic/profiles/students/57").header(HttpHeaders.AUTHORIZATION, viewer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/academic/profiles/faculty/11").header(HttpHeaders.AUTHORIZATION,
                bearer("ROLE_FACULTY", "ACADEMIC_PROFILE_VIEW", "ACADEMIC_PROFILE_MANAGE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(admin);

        mockMvc.perform(get("/api/v1/admin/academic/profiles/students").header(HttpHeaders.AUTHORIZATION, viewer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/academic/profiles/faculty").header(HttpHeaders.AUTHORIZATION, viewer))
                .andExpect(status().isOk());
    }
}
