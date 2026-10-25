package com.stacc.backend.hostel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import com.stacc.backend.hostel.HostelAllocationService;
import com.stacc.backend.hostel.HostelApplicationService;
import com.stacc.backend.hostel.HostelManagementService;
import com.stacc.backend.hostel.HostelQueryService;
import com.stacc.backend.hostel.HostelRequests.CreateHostelApplicationRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the hostel routes through Spring Security, with the hostel services as stand-ins. */
class HostelControllerTest extends DatabaseFreeApiTest {

    private static final String STUDENT = "/api/hostel/student";
    private static final String ADMIN = "/api/hostel/admin";

    @MockitoBean
    private HostelApplicationService applications;

    @MockitoBean
    private HostelQueryService queries;

    @MockitoBean
    private HostelManagementService management;

    @MockitoBean
    private HostelAllocationService allocations;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static String student() {
        return token(70, "ROLE_STUDENT", "HOSTEL_APPLICATION_CREATE", "HOSTEL_APPLICATION_VIEW_OWN", "HOSTEL_APPLICATION_CANCEL");
    }

    private static String admin() {
        return token(1, "ROLE_ADMIN", "HOSTEL_MANAGE", ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> studentRoutes() {
        return new ArrayList<>(List.of(get(STUDENT + "/eligible-sessions"), get(STUDENT + "/applications"),
                get(STUDENT + "/applications/42"), json(post(STUDENT + "/applications"), "{\"academicSessionId\":2}"),
                post(STUDENT + "/applications/42/cancel")));
    }

    private static List<MockHttpServletRequestBuilder> adminRoutes() {
        return new ArrayList<>(List.of(get(ADMIN + "/hostels"), get(ADMIN + "/hostels/1"),
                json(post(ADMIN + "/hostels"), "{\"code\":\"MAIN\",\"name\":\"Main\"}"),
                json(put(ADMIN + "/hostels/1"), "{\"name\":\"Main\",\"active\":true}"),
                get(ADMIN + "/hostels/1/rooms"), json(post(ADMIN + "/hostels/1/rooms"), "{\"roomNumber\":\"A-101\"}"),
                json(put(ADMIN + "/rooms/2"), "{\"roomNumber\":\"A-101\",\"active\":true}"),
                get(ADMIN + "/rooms/2/beds"), json(post(ADMIN + "/rooms/2/beds"), "{\"bedNumber\":\"B1\"}"),
                json(put(ADMIN + "/beds/3"), "{\"bedNumber\":\"B1\",\"active\":true}"),
                get(ADMIN + "/applications"), get(ADMIN + "/applications/42"), get(ADMIN + "/available-beds"),
                json(post(ADMIN + "/applications/42/allocations"), "{\"bedId\":3}"), get(ADMIN + "/allocations"),
                post(ADMIN + "/allocations/77/vacate")));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        List<MockHttpServletRequestBuilder> all = studentRoutes();
        all.addAll(adminRoutes());
        for (MockHttpServletRequestBuilder request : all) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(applications, queries, management, allocations);
    }

    @Test
    void studentRoutesNeedTheStudentRoleWithTheHostelPermissions() throws Exception {
        for (MockHttpServletRequestBuilder request : studentRoutes()) {
            assertThat(statusOf(request, student())).isBetween(200, 201);
        }
        String facultyWithPermissions = token(80, "ROLE_FACULTY", "HOSTEL_APPLICATION_CREATE", "HOSTEL_APPLICATION_VIEW_OWN",
                "HOSTEL_APPLICATION_CANCEL");
        for (String denied : new String[] {token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW"), facultyWithPermissions, admin()}) {
            for (MockHttpServletRequestBuilder request : studentRoutes()) {
                assertThat(statusOf(request, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void adminRoutesNeedTheAdminRoleWithHostelManage() throws Exception {
        for (MockHttpServletRequestBuilder request : adminRoutes()) {
            assertThat(statusOf(request, admin())).isBetween(200, 201);
        }
        for (String denied : new String[] {student(), token(1, "ROLE_ADMIN", ApprovalActor.TASK_DECIDE),
                token(70, "ROLE_STUDENT", "HOSTEL_MANAGE"), token(80, "ROLE_FACULTY", "HOSTEL_MANAGE")}) {
            for (MockHttpServletRequestBuilder request : adminRoutes()) {
                assertThat(statusOf(request, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void theStudentAndTheAdminAlwaysComeFromTheToken() throws Exception {
        mockMvc.perform(json(post(STUDENT + "/applications"),
                        "{\"academicSessionId\":2,\"note\":\"Hi\",\"studentProfileId\":9,\"approvalRequestId\":1,\"applicationNumber\":\"X\"}")
                .header(HttpHeaders.AUTHORIZATION, student())).andExpect(status().isCreated());
        verify(applications).apply(70L, new CreateHostelApplicationRequest(2L, "Hi"));

        mockMvc.perform(json(post(ADMIN + "/applications/42/allocations"), "{\"bedId\":3,\"allocatedBy\":5}")
                .header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isCreated());
        verify(allocations).allocate(1L, 42L, 3L);

        mockMvc.perform(get(ADMIN + "/applications/42").header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isOk());
        ArgumentCaptor<ApprovalActor> actor = ArgumentCaptor.forClass(ApprovalActor.class);
        verify(queries).adminApplication(actor.capture(), eq(42L));
        assertThat(actor.getValue().accountId()).isEqualTo(1L);
        assertThat(actor.getValue().taskDecide()).isTrue();
    }

    @Test
    void invalidRequestsAre400BeforeTheServices() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(json(post(STUDENT + "/applications"), "{}"),
                json(post(STUDENT + "/applications"), "{\"academicSessionId\":2,\"note\":\"" + "x".repeat(3001) + "\"}"))) {
            assertThat(statusOf(request, student())).isEqualTo(400);
        }
        for (MockHttpServletRequestBuilder request : List.of(
                json(post(ADMIN + "/hostels"), "{\"code\":\"\",\"name\":\"Main\"}"),
                json(put(ADMIN + "/hostels/1"), "{\"name\":\"Main\"}"),
                json(post(ADMIN + "/hostels/1/rooms"), "{\"roomNumber\":\" \"}"),
                json(put(ADMIN + "/beds/3"), "{\"bedNumber\":\"B1\"}"),
                json(post(ADMIN + "/applications/42/allocations"), "{}"),
                get(ADMIN + "/applications").param("approvalStatus", "WAITING"),
                get(ADMIN + "/allocations").param("status", "OCCUPIED"))) {
            assertThat(statusOf(request, admin())).isEqualTo(400);
        }
        verifyNoInteractions(applications, management, allocations);
    }

    @Test
    void nothingCanBeDeletedAndThereIsNoHostelApprovalRoute() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(delete(ADMIN + "/hostels/1"), delete(ADMIN + "/rooms/2"),
                delete(ADMIN + "/beds/3"), delete(ADMIN + "/allocations/77"), delete(STUDENT + "/applications/42"),
                post(ADMIN + "/applications/42/approve"), post(ADMIN + "/applications/42/reject"))) {
            assertThat(statusOf(request, admin())).isIn(404, 405);
        }
        verifyNoInteractions(applications, management, allocations);
    }

    @Test
    void theRoutesAreDocumentedWithBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + STUDENT + "/eligible-sessions'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + STUDENT + "/applications'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + STUDENT + "/applications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + STUDENT + "/applications/{applicationId}/cancel'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/hostels/{hostelId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.paths['" + ADMIN + "/rooms/{roomId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/beds/{bedId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/available-beds'].get").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/applications/{applicationId}/allocations'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN + "/allocations/{allocationId}/vacate'].post.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateHostelApplicationRequest.properties", not(hasKey("studentProfileId"))))
                .andExpect(jsonPath("$.components.schemas.HostelApplicationDetail.properties", not(hasKey("studentProfileId"))))
                .andExpect(jsonPath("$.components.schemas.UpdateHostelRequest.properties", not(hasKey("code"))));
    }
}
