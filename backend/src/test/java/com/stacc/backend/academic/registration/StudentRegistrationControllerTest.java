package com.stacc.backend.academic.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationOverview;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Course registration routes: the student role with each route's own permission, the student from the token. */
class StudentRegistrationControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private StudentRegistrationService service;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("someone", 70, authorities);
    }

    @Test
    void theOverviewNeedsTheStudentRoleAndTheViewPermission() throws Exception {
        when(service.overview(anyLong())).thenReturn(new RegistrationOverview(true, List.of()));
        String path = "/api/v1/students/me/course-registration";
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY", "COURSE_REGISTRATION_VIEW")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT", "COURSE_REGISTRATION_VIEW")))
                .andExpect(status().isOk());
        verify(service).overview(70L);
    }

    @Test
    void registeringNeedsTheRegisterPermissionAndAValidList() throws Exception {
        when(service.register(anyLong(), any())).thenReturn(new RegistrationResult(List.of(), 0, BigDecimal.ZERO));
        String path = "/api/v1/students/me/course-registration";
        String student = bearer("ROLE_STUDENT", "COURSE_REGISTER");
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT", "COURSE_REGISTRATION_VIEW"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseOfferingIds\":[101]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseOfferingIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);

        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"courseOfferingIds\":[101,102],\"studentId\":5}"))
                .andExpect(status().isCreated());
        verify(service).register(eq(70L), eq(List.of(101L, 102L)));
    }

    @Test
    void droppingNeedsTheDropPermission() throws Exception {
        String path = "/api/v1/students/me/course-enrollments/601/drop";
        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT", "COURSE_REGISTER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_STUDENT", "COURSE_DROP")))
                .andExpect(status().isOk());
        verify(service).drop(70L, 601L);
    }
}
