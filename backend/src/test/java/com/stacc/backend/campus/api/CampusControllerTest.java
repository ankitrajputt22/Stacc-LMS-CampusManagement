package com.stacc.backend.campus.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.campus.CampusActor;
import com.stacc.backend.campus.notices.CampusNoticeQueryService;
import com.stacc.backend.campus.notices.CampusNoticeService;
import com.stacc.backend.campus.services.CampusServiceDefinitionService;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceRequestInput;
import com.stacc.backend.campus.services.CampusServiceQueryService;
import com.stacc.backend.campus.services.CampusServiceRequestService;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the campus routes through Spring Security, with the campus services as stand-ins. */
class CampusControllerTest extends DatabaseFreeApiTest {

    private static final String CAMPUS = "/api/campus";
    private static final String ADMIN = "/api/campus/admin";

    @MockitoBean
    private CampusServiceDefinitionService services;

    @MockitoBean
    private CampusServiceRequestService requests;

    @MockitoBean
    private CampusServiceQueryService queries;

    @MockitoBean
    private CampusNoticeService notices;

    @MockitoBean
    private CampusNoticeQueryService noticeQueries;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static final String[] REQUESTER = {"CAMPUS_SERVICE_REQUEST_CREATE", "CAMPUS_SERVICE_REQUEST_VIEW_OWN",
            "CAMPUS_SERVICE_REQUEST_CANCEL", "CAMPUS_NOTICE_VIEW"};

    private static String student() {
        return token(70, concat("ROLE_STUDENT", REQUESTER));
    }

    private static String faculty() {
        return token(80, concat("ROLE_FACULTY", REQUESTER));
    }

    private static String admin() {
        return token(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE", "CAMPUS_NOTICE_VIEW", "CAMPUS_NOTICE_MANAGE",
                ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE);
    }

    private static String[] concat(String first, String[] rest) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(rest));
        return all.toArray(String[]::new);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> requesterRoutes() {
        return new ArrayList<>(List.of(get(CAMPUS + "/services"),
                json(post(CAMPUS + "/requests"), "{\"serviceId\":1,\"subject\":\"S\",\"details\":\"D\"}"),
                get(CAMPUS + "/requests/me"), get(CAMPUS + "/requests/42"), post(CAMPUS + "/requests/42/cancel")));
    }

    private static List<MockHttpServletRequestBuilder> serviceAdminRoutes() {
        String service = "{\"code\":\"ID_CARD\",\"name\":\"ID card\",\"availableToStudent\":true,"
                + "\"availableToFaculty\":false,\"approvalRequired\":false}";
        String update = "{\"name\":\"ID card\",\"active\":true,\"availableToStudent\":true,"
                + "\"availableToFaculty\":false,\"approvalRequired\":false}";
        return new ArrayList<>(List.of(get(ADMIN + "/services"), get(ADMIN + "/services/1"),
                json(post(ADMIN + "/services"), service), json(put(ADMIN + "/services/1"), update),
                get(ADMIN + "/requests"), get(ADMIN + "/requests/42"), post(ADMIN + "/requests/42/complete")));
    }

    private static List<MockHttpServletRequestBuilder> noticeAdminRoutes() {
        String notice = "{\"title\":\"T\",\"message\":\"M\",\"visibleToStudent\":true,\"visibleToFaculty\":false,"
                + "\"visibleToAdmin\":false}";
        return new ArrayList<>(List.of(get(ADMIN + "/notices"), get(ADMIN + "/notices/5"),
                json(post(ADMIN + "/notices"), notice), json(put(ADMIN + "/notices/5"), notice),
                post(ADMIN + "/notices/5/publish"), post(ADMIN + "/notices/5/archive")));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        List<MockHttpServletRequestBuilder> all = requesterRoutes();
        all.addAll(serviceAdminRoutes());
        all.addAll(noticeAdminRoutes());
        all.add(get(CAMPUS + "/notices"));
        for (MockHttpServletRequestBuilder request : all) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(services, requests, queries, notices, noticeQueries);
    }

    @Test
    void requesterRoutesNeedStudentOrFacultyWithTheCampusPermissions() throws Exception {
        for (String allowed : new String[] {student(), faculty()}) {
            for (MockHttpServletRequestBuilder request : requesterRoutes()) {
                assertThat(statusOf(request, allowed)).isBetween(200, 201);
            }
        }
        String adminWithPermissions = token(1, concat("ROLE_ADMIN", REQUESTER));
        for (String denied : new String[] {token(70, "ROLE_STUDENT", "CAMPUS_NOTICE_VIEW"), adminWithPermissions, admin()}) {
            for (MockHttpServletRequestBuilder request : requesterRoutes()) {
                assertThat(statusOf(request, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void theNoticeFeedIsForAnyOfTheThreeRolesWithNoticeView() throws Exception {
        for (String allowed : new String[] {student(), faculty(), admin()}) {
            assertThat(statusOf(get(CAMPUS + "/notices"), allowed)).isEqualTo(200);
        }
        for (String denied : new String[] {token(70, "ROLE_STUDENT"), token(5, "CAMPUS_NOTICE_VIEW")}) {
            assertThat(statusOf(get(CAMPUS + "/notices"), denied)).isEqualTo(403);
        }
    }

    @Test
    void adminRoutesNeedTheAdminRoleWithTheirOwnManagePermission() throws Exception {
        for (MockHttpServletRequestBuilder request : serviceAdminRoutes()) {
            assertThat(statusOf(request, admin())).isBetween(200, 201);
        }
        for (MockHttpServletRequestBuilder request : noticeAdminRoutes()) {
            assertThat(statusOf(request, admin())).isBetween(200, 201);
        }
        String noticeOnlyAdmin = token(1, "ROLE_ADMIN", "CAMPUS_NOTICE_MANAGE", ApprovalActor.TASK_DECIDE);
        String serviceOnlyAdmin = token(1, "ROLE_ADMIN", "CAMPUS_SERVICE_MANAGE");
        for (String denied : new String[] {student(), noticeOnlyAdmin, token(70, "ROLE_STUDENT", "CAMPUS_SERVICE_MANAGE"),
                token(80, "ROLE_FACULTY", "CAMPUS_SERVICE_MANAGE")}) {
            for (MockHttpServletRequestBuilder request : serviceAdminRoutes()) {
                assertThat(statusOf(request, denied)).isEqualTo(403);
            }
        }
        for (String denied : new String[] {faculty(), serviceOnlyAdmin, token(70, "ROLE_STUDENT", "CAMPUS_NOTICE_MANAGE")}) {
            for (MockHttpServletRequestBuilder request : noticeAdminRoutes()) {
                assertThat(statusOf(request, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void theCallerAndItsRolesAlwaysComeFromTheToken() throws Exception {
        mockMvc.perform(json(post(CAMPUS + "/requests"),
                        "{\"serviceId\":1,\"subject\":\"S\",\"details\":\"D\",\"requesterId\":9,\"status\":\"COMPLETED\",\"requestNumber\":\"X\"}")
                .header(HttpHeaders.AUTHORIZATION, token(90, concat("ROLE_STUDENT", concat("ROLE_FACULTY", REQUESTER)))))
                .andExpect(status().isCreated());
        ArgumentCaptor<CampusActor> actor = ArgumentCaptor.forClass(CampusActor.class);
        verify(requests).create(actor.capture(), eq(new CreateCampusServiceRequestInput(1L, "S", "D")));
        assertThat(actor.getValue().accountId()).isEqualTo(90L);
        assertThat(actor.getValue().roleCodes()).containsExactlyInAnyOrder("STUDENT", "FACULTY");

        mockMvc.perform(get(CAMPUS + "/requests/me").param("status", "APPROVED").param("serviceId", "2")
                .header(HttpHeaders.AUTHORIZATION, student())).andExpect(status().isOk());
        verify(queries).ownRequests(70L, CampusServiceRequestStatus.APPROVED, 2L, 0, 20);

        mockMvc.perform(post(CAMPUS + "/requests/42/cancel").header(HttpHeaders.AUTHORIZATION, faculty()))
                .andExpect(status().isOk());
        verify(requests).cancel(80L, 42L);

        mockMvc.perform(post(ADMIN + "/requests/42/complete").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk());
        ArgumentCaptor<ApprovalActor> adminActor = ArgumentCaptor.forClass(ApprovalActor.class);
        verify(requests).complete(adminActor.capture(), eq(42L));
        assertThat(adminActor.getValue().accountId()).isEqualTo(1L);
        assertThat(adminActor.getValue().taskDecide()).isTrue();
    }

    @Test
    void invalidRequestsAre400BeforeTheServices() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(json(post(CAMPUS + "/requests"), "{}"),
                json(post(CAMPUS + "/requests"), "{\"serviceId\":1,\"subject\":\"" + "x".repeat(201) + "\",\"details\":\"D\"}"),
                json(post(CAMPUS + "/requests"), "{\"serviceId\":1,\"subject\":\"S\",\"details\":\"" + "x".repeat(10_001) + "\"}"),
                get(CAMPUS + "/requests/me").param("status", "PENDING"))) {
            assertThat(statusOf(request, student())).isEqualTo(400);
        }
        for (MockHttpServletRequestBuilder request : List.of(
                json(post(ADMIN + "/services"), "{\"code\":\"\",\"name\":\"X\",\"availableToStudent\":true,\"availableToFaculty\":true,\"approvalRequired\":false}"),
                json(put(ADMIN + "/services/1"), "{\"name\":\"X\"}"),
                json(post(ADMIN + "/notices"), "{\"title\":\"T\",\"message\":\" \",\"visibleToStudent\":true,\"visibleToFaculty\":true,\"visibleToAdmin\":true}"),
                json(put(ADMIN + "/notices/5"), "{\"title\":\"T\",\"message\":\"M\"}"),
                get(ADMIN + "/requests").param("status", "WAITING"),
                get(ADMIN + "/notices").param("status", "DELETED"))) {
            assertThat(statusOf(request, admin())).isEqualTo(400);
        }
        verifyNoInteractions(services, requests, notices);
    }

    @Test
    void nothingCanBeDeletedAndThereIsNoCampusApprovalRoute() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(delete(ADMIN + "/services/1"), delete(ADMIN + "/requests/42"),
                delete(ADMIN + "/notices/5"), delete(CAMPUS + "/requests/42"), post(ADMIN + "/requests/42/approve"),
                post(ADMIN + "/requests/42/reject"), post(ADMIN + "/notices/5/restore"),
                post(ADMIN + "/notices/5/unarchive"), post(CAMPUS + "/requests/42/messages"))) {
            assertThat(statusOf(request, admin())).isIn(404, 405);
        }
        verifyNoInteractions(services, requests, notices);
    }

    @Test
    void theNoticeFeedUsesTheTokenRolesAndPaging() throws Exception {
        mockMvc.perform(get(CAMPUS + "/notices").param("page", "2").param("size", "5")
                .header(HttpHeaders.AUTHORIZATION, token(90, "ROLE_FACULTY", "ROLE_ADMIN", "CAMPUS_NOTICE_VIEW")))
                .andExpect(status().isOk());
        ArgumentCaptor<CampusActor> actor = ArgumentCaptor.forClass(CampusActor.class);
        verify(noticeQueries).feed(actor.capture(), eq(2), eq(5));
        assertThat(actor.getValue().roleCodes()).containsExactlyInAnyOrder("FACULTY", "ADMIN");
        verify(noticeQueries, org.mockito.Mockito.never()).adminNotices(any(), anyInt(), anyInt());
    }

    @Test
    void theRoutesAreDocumentedWithBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + CAMPUS + "/services'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + CAMPUS + "/requests'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + CAMPUS + "/requests/me'].get").exists())
                .andExpect(jsonPath("$.paths['" + CAMPUS + "/requests/{requestId}/cancel'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + CAMPUS + "/notices'].get").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/services/{serviceId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.paths['" + ADMIN + "/requests/{requestId}/complete'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/notices/{noticeId}/publish'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/notices/{noticeId}/archive'].post").exists())
                .andExpect(jsonPath("$.components.schemas.CreateCampusServiceRequestInput.properties", not(hasKey("requesterId"))))
                .andExpect(jsonPath("$.components.schemas.CreateCampusServiceRequestInput.properties", not(hasKey("status"))))
                .andExpect(jsonPath("$.components.schemas.UpdateCampusServiceInput.properties", not(hasKey("code"))))
                .andExpect(jsonPath("$.components.schemas.CampusNoticeInput.properties", not(hasKey("status"))))
                .andExpect(jsonPath("$.components.schemas.CampusServiceSummary.properties", not(hasKey("approvalWorkflowCode"))));
    }
}
