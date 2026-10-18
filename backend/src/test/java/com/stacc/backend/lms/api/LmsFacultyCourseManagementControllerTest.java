package com.stacc.backend.lms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.management.LmsCourseChange;
import com.stacc.backend.lms.management.LmsCourseManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls the faculty LMS course management routes through Spring Security. The services are stand-ins, so these tests
 * are about who gets in, what reaches the services, and what is sent back.
 */
class LmsFacultyCourseManagementControllerTest extends DatabaseFreeApiTest {

    private static final String ROOT = "/api/lms/faculty";
    private static final long ACCOUNT = 20L;

    @MockitoBean
    private LmsFacultyManagedCourseQueryService queryService;

    @MockitoBean
    private LmsCourseManagementService managementService;

    private static String token(String... authorities) {
        return TestTokens.valid("EMP-TEST-20", ACCOUNT, authorities);
    }

    private static String manager() {
        return token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE", "LMS_CONTENT_MANAGE");
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(get(ROOT + "/managed-courses"), post(ROOT + "/course-offerings/31/lms-course"),
                post(ROOT + "/courses/42/sync-student-memberships"), post(ROOT + "/courses/42/publish"),
                post(ROOT + "/courses/42/archive"));
    }

    private void refused(List<String> tokens, Supplier<List<MockHttpServletRequestBuilder>> requests) throws Exception {
        for (String denied : tokens) {
            for (MockHttpServletRequestBuilder request : requests.get()) {
                mockMvc.perform(as(denied, request)).andExpect(status().isForbidden());
            }
        }
        for (MockHttpServletRequestBuilder request : requests.get()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queryService, managementService);
    }

    @Test
    void everyRouteNeedsTheFacultyRoleTheCourseViewAndTheCourseManagePermissionTogether() throws Exception {
        refused(List.of(
                token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_CONTENT_MANAGE", "LMS_ASSIGNMENT_MANAGE", "LMS_ASSIGNMENT_GRADE"),
                token("ROLE_FACULTY", "LMS_COURSE_MANAGE"),
                token("FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE"),
                token("ROLE_ADMIN", "ACADEMIC_OFFERING_VIEW", "ACADEMIC_OFFERING_MANAGE", "ACADEMIC_ENROLLMENT_MANAGE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_COURSE_MANAGE")), LmsFacultyCourseManagementControllerTest::routes);
    }

    @Test
    void theManagedCoursesComeFromTheAccountInTheTokenAndKeepOfferingsWithoutAnLmsCourse() throws Exception {
        when(queryService.getManagedCourses(ACCOUNT)).thenReturn(List.of(
                new LmsFacultyManagedCourseResponse(33L, "CS301", "Data Structures", new BigDecimal("4.00"), 3, "BTECH", "2026-27", null, null),
                new LmsFacultyManagedCourseResponse(34L, "CS303", "Networks", new BigDecimal("4.00"), 3, "BTECH", "2026-27", 44L, "ARCHIVED")));

        mockMvc.perform(as(manager(), get(ROOT + "/managed-courses").param("facultyProfileId", "9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseOfferingId").value(33))
                .andExpect(jsonPath("$[0].lmsCourseId").isEmpty())
                .andExpect(jsonPath("$[0].lmsCourseStatus").isEmpty())
                .andExpect(jsonPath("$[1].lmsCourseStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$[0].teachingAssignmentId").doesNotExist())
                .andExpect(jsonPath("$[0].facultyProfileId").doesNotExist());
        verify(queryService).getManagedCourses(ACCOUNT);
    }

    @Test
    void lifecycleChangesTakeTheCourseFromThePathAndTheFacultyFromTheToken() throws Exception {
        when(managementService.create(ACCOUNT, 31L)).thenReturn(new LmsCourseChange(42L, 31L, LmsCourseStatus.DRAFT, 2));
        when(managementService.synchronizeStudentMemberships(ACCOUNT, 42L)).thenReturn(new LmsCourseChange(42L, 31L, LmsCourseStatus.DRAFT, 2));
        when(managementService.publish(ACCOUNT, 42L)).thenReturn(new LmsCourseChange(42L, 31L, LmsCourseStatus.PUBLISHED, 2));
        when(managementService.archive(ACCOUNT, 42L)).thenReturn(new LmsCourseChange(42L, 31L, LmsCourseStatus.ARCHIVED, null));

        mockMvc.perform(as(manager(), post(ROOT + "/course-offerings/31/lms-course")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lmsCourseId").value(42))
                .andExpect(jsonPath("$.lmsCourseStatus").value("DRAFT"))
                .andExpect(jsonPath("$.synchronizedCount").value(2));
        mockMvc.perform(as(manager(), post(ROOT + "/courses/42/sync-student-memberships"))).andExpect(status().isOk());
        mockMvc.perform(as(manager(), post(ROOT + "/courses/42/publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lmsCourseStatus").value("PUBLISHED"));
        mockMvc.perform(as(manager(), post(ROOT + "/courses/42/archive")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lmsCourseStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.synchronizedCount").isEmpty());
    }

    @Test
    void refusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(managementService.create(eq(ACCOUNT), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "An LMS course already exists for this course offering."));
        when(managementService.publish(eq(ACCOUNT), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "LMS course not found."));

        mockMvc.perform(as(manager(), post(ROOT + "/course-offerings/31/lms-course")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An LMS course already exists for this course offering."));
        mockMvc.perform(as(manager(), post(ROOT + "/courses/42/publish")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("LMS course not found."));
        mockMvc.perform(as(manager(), post(ROOT + "/courses/forty/archive"))).andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoDeleteAndNoRestore() throws Exception {
        mockMvc.perform(as(manager(), delete(ROOT + "/courses/42/archive"))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(manager(), delete(ROOT + "/course-offerings/31/lms-course"))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(as(manager(), post(ROOT + "/courses/42/restore"))).andExpect(status().isNotFound());
        verifyNoInteractions(managementService);
    }

    @Test
    void everyRouteIsDocumentedWithTheBearerScheme() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        String[][] operations = {
                {"/managed-courses", "get"}, {"/course-offerings/{courseOfferingId}/lms-course", "post"},
                {"/courses/{lmsCourseId}/sync-student-memberships", "post"}, {"/courses/{lmsCourseId}/publish", "post"},
                {"/courses/{lmsCourseId}/archive", "post"}};
        for (String[] operation : operations) {
            String path = "$.paths['" + ROOT + operation[0] + "']." + operation[1];
            result.andExpect(jsonPath(path + ".security[0].bearerAuth").exists())
                    .andExpect(jsonPath(path + ".responses['401']").exists())
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        result.andExpect(jsonPath("$.paths['" + ROOT + "/course-offerings/{courseOfferingId}/lms-course'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + ROOT + "/course-offerings/{courseOfferingId}/lms-course'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ROOT + "/courses/{lmsCourseId}/publish'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ROOT + "/courses/{lmsCourseId}/archive'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['" + ROOT + "/courses/{lmsCourseId}/restore']").doesNotExist());
    }
}
