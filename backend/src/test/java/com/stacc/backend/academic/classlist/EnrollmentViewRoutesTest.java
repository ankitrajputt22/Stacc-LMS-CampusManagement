package com.stacc.backend.academic.classlist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.academic.admin.EnrollmentMonitorService;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.Filters;
import com.stacc.backend.academic.admin.EnrollmentMonitorService.MonitorPage;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The admin enrollment monitor and the faculty class lists: each needs its own role and permission. */
class EnrollmentViewRoutesTest extends DatabaseFreeApiTest {

    @MockitoBean
    private EnrollmentMonitorService monitor;

    @MockitoBean
    private FacultyClassListService classLists;

    private static String bearer(String... authorities) {
        return "Bearer " + TestTokens.valid("someone", 70, authorities);
    }

    @Test
    void theMonitorNeedsAnAdminWhoMayViewEnrollments() throws Exception {
        when(monitor.search(any(), anyInt(), anyInt())).thenReturn(new MonitorPage(List.of(), 0, 20, 0, 0));
        String path = "/api/v1/admin/academic/enrollments/course-enrollments";
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY", "ACADEMIC_ENROLLMENT_VIEW")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(monitor);

        mockMvc.perform(get(path).param("status", "DROPPED").param("semesterId", "5").param("q", "cs3")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "ACADEMIC_ENROLLMENT_VIEW")))
                .andExpect(status().isOk());
        verify(monitor).search(new Filters(null, null, 5L, null, null, CourseEnrollmentStatus.DROPPED, "cs3"), 0, 20);
    }

    @Test
    void classListsNeedFacultyWhoMayViewEnrolledStudents() throws Exception {
        when(classLists.myOfferings(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/faculty/me/course-offerings").header(HttpHeaders.AUTHORIZATION, bearer("ROLE_FACULTY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/faculty/me/course-offerings/101/students")
                .header(HttpHeaders.AUTHORIZATION, bearer("ROLE_ADMIN", "ENROLLED_STUDENTS_VIEW")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(classLists);

        String faculty = bearer("ROLE_FACULTY", "ENROLLED_STUDENTS_VIEW");
        mockMvc.perform(get("/api/v1/faculty/me/course-offerings").header(HttpHeaders.AUTHORIZATION, faculty))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/faculty/me/course-offerings/101/students").header(HttpHeaders.AUTHORIZATION, faculty))
                .andExpect(status().isOk());
        verify(classLists).myOfferings(70L);
        verify(classLists).classList(eq(70L), eq(101L));
    }
}
