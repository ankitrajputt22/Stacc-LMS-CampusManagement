package com.stacc.backend.approval.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestPage;
import com.stacc.backend.approval.ApprovalWorkflowDefinitionService;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.token.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the approval routes through Spring Security, with the approval services as stand-ins. */
class ApprovalControllerTest extends DatabaseFreeApiTest {

    private static final String ME = "/api/approvals/me";
    private static final String REQUEST = "/api/approvals/requests/12";
    private static final String TASKS = "/api/approvals/tasks";
    private static final String WORKFLOWS = "/api/approvals/admin/workflows";
    private static final String WORKFLOW_BODY =
            "{\"code\":\"DEMO\",\"name\":\"Demo\",\"steps\":[{\"name\":\"Review\",\"approverRoleCode\":\"FACULTY\"}]}";
    private static final String UPDATE_BODY =
            "{\"name\":\"Demo\",\"active\":true,\"steps\":[{\"name\":\"Review\",\"approverRoleCode\":\"ADMIN\"}]}";

    @MockitoBean
    private ApprovalQueryService queries;

    @MockitoBean
    private ApprovalWorkflowService workflow;

    @MockitoBean
    private ApprovalWorkflowDefinitionService definitions;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static String student() {
        return token(70, "ROLE_STUDENT", ApprovalActor.REQUEST_VIEW_OWN);
    }

    private static String faculty() {
        return token(80, "ROLE_FACULTY", ApprovalActor.REQUEST_VIEW_OWN, ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE);
    }

    private static String admin() {
        return token(1, "ROLE_ADMIN", ApprovalActor.REQUEST_VIEW_OWN, ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE,
                ApprovalActor.WORKFLOW_MANAGE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> everyRoute() {
        return new ArrayList<>(List.of(get(ME), get(REQUEST), get(TASKS),
                json(post(TASKS + "/121/approve"), "{}"), json(post(TASKS + "/121/reject"), "{\"comment\":\"No.\"}"),
                get(WORKFLOWS), get(WORKFLOWS + "/4"), json(post(WORKFLOWS), WORKFLOW_BODY),
                json(put(WORKFLOWS + "/4"), UPDATE_BODY)));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        for (MockHttpServletRequestBuilder request : everyRoute()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queries, workflow, definitions);
    }

    @Test
    void rolesWithoutApprovalPermissionsAndPermissionsWithoutARoleAreForbidden() throws Exception {
        for (String denied : new String[] {token(70, "ROLE_STUDENT", "NOTIFICATION_VIEW"),
                token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"), token(1, "ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW"),
                token(5, ApprovalActor.REQUEST_VIEW_OWN, ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE,
                        ApprovalActor.WORKFLOW_MANAGE)}) {
            for (MockHttpServletRequestBuilder request : everyRoute()) {
                assertThat(statusOf(request, denied)).isEqualTo(HttpStatus.FORBIDDEN.value());
            }
        }
        verifyNoInteractions(queries, workflow, definitions);
    }

    @Test
    void studentsReachOnlyTheirOwnRequests() throws Exception {
        assertThat(statusOf(get(ME), student())).isEqualTo(200);
        assertThat(statusOf(get(REQUEST), student())).isEqualTo(200);
        assertThat(statusOf(get(TASKS), student())).isEqualTo(403);
        assertThat(statusOf(json(post(TASKS + "/121/approve"), "{}"), student())).isEqualTo(403);
        assertThat(statusOf(get(WORKFLOWS), student())).isEqualTo(403);
    }

    @Test
    void facultyReachTasksButNotWorkflowConfiguration() throws Exception {
        assertThat(statusOf(get(TASKS), faculty())).isEqualTo(200);
        assertThat(statusOf(json(post(TASKS + "/121/approve"), "{}"), faculty())).isEqualTo(200);
        assertThat(statusOf(json(post(TASKS + "/121/reject"), "{\"comment\":\"No.\"}"), faculty())).isEqualTo(200);
        assertThat(statusOf(get(WORKFLOWS), faculty())).isEqualTo(403);
        assertThat(statusOf(json(post(WORKFLOWS), WORKFLOW_BODY), token(80, "ROLE_FACULTY",
                ApprovalActor.WORKFLOW_MANAGE))).isEqualTo(403);
    }

    @Test
    void viewingTasksAndDecidingAreSeparatePermissions() throws Exception {
        String viewOnly = token(80, "ROLE_FACULTY", ApprovalActor.TASK_VIEW);
        assertThat(statusOf(get(TASKS), viewOnly)).isEqualTo(200);
        assertThat(statusOf(json(post(TASKS + "/121/approve"), "{}"), viewOnly)).isEqualTo(403);
    }

    @Test
    void adminsReachEveryRoute() throws Exception {
        for (MockHttpServletRequestBuilder request : everyRoute()) {
            assertThat(statusOf(request, admin())).isBetween(200, 201);
        }
    }

    @Test
    void theActorAlwaysComesFromTheToken() throws Exception {
        when(queries.ownRequests(any(), any(), anyInt(), anyInt())).thenReturn(new ApprovalRequestPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(ME).param("status", "PENDING").param("requesterUserAccountId", "1")
                .header(HttpHeaders.AUTHORIZATION, student())).andExpect(status().isOk());
        mockMvc.perform(json(post(TASKS + "/121/approve"), "{\"comment\":\"Fine.\",\"actedBy\":1,\"accountId\":1}")
                .header(HttpHeaders.AUTHORIZATION, faculty())).andExpect(status().isOk());

        ArgumentCaptor<ApprovalActor> viewer = ArgumentCaptor.forClass(ApprovalActor.class);
        verify(queries).ownRequests(viewer.capture(), eq(ApprovalRequestStatus.PENDING), eq(0), eq(20));
        assertThat(viewer.getValue().accountId()).isEqualTo(70L);
        ArgumentCaptor<ApprovalActor> decider = ArgumentCaptor.forClass(ApprovalActor.class);
        verify(workflow).approveStep(decider.capture(), eq(121L), eq("Fine."));
        assertThat(decider.getValue().accountId()).isEqualTo(80L);
        assertThat(decider.getValue().roleCodes()).containsExactly("FACULTY");
    }

    @Test
    void approvingWithoutABodyIsAllowed() throws Exception {
        mockMvc.perform(post(TASKS + "/121/approve").header(HttpHeaders.AUTHORIZATION, faculty())).andExpect(status().isOk());
        verify(workflow).approveStep(any(), eq(121L), isNull());
    }

    @Test
    void invalidRequestsAre400BeforeTheServices() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                json(post(TASKS + "/121/reject"), "{\"comment\":\"  \"}"),
                json(post(TASKS + "/121/reject"), "{}"),
                json(post(TASKS + "/121/approve"), "{\"comment\":\"" + "x".repeat(5001) + "\"}"),
                json(post(WORKFLOWS), "{\"code\":\"DEMO\",\"name\":\"Demo\",\"steps\":[]}"),
                json(post(WORKFLOWS), "{\"code\":\"DEMO\",\"name\":\"Demo\"}"),
                json(post(WORKFLOWS), "{\"code\":\"\",\"name\":\"Demo\",\"steps\":[{\"name\":\"R\",\"approverRoleCode\":\"ADMIN\"}]}"),
                json(post(WORKFLOWS), "{\"code\":\"DEMO\",\"name\":\"Demo\",\"steps\":[{\"name\":\"\",\"approverRoleCode\":\"ADMIN\"}]}"),
                json(put(WORKFLOWS + "/4"), "{\"name\":\"Demo\",\"steps\":[{\"name\":\"R\",\"approverRoleCode\":\"ADMIN\"}]}"),
                get(ME).param("status", "WAITING"))) {
            assertThat(statusOf(request, admin())).isEqualTo(400);
        }
        verifyNoInteractions(queries, workflow, definitions);
    }

    @Test
    void thereIsNoRouteToStartCancelOrDeleteAnything() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                json(post("/api/approvals/requests"), "{\"workflowCode\":\"DEMO\",\"requesterId\":1}"),
                json(post("/api/approvals"), "{}"),
                post(REQUEST + "/cancel"), delete(REQUEST), delete(WORKFLOWS + "/4"), delete(TASKS + "/121"))) {
            assertThat(statusOf(request, admin())).isIn(404, 405);
        }
        verifyNoInteractions(queries, workflow, definitions);
    }

    @Test
    void theRoutesAreDocumentedWithBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + ME + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/approvals/requests/{approvalRequestId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/approvals/requests/{approvalRequestId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.paths['/api/approvals/requests']").doesNotExist())
                .andExpect(jsonPath("$.paths['" + TASKS + "'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['" + TASKS + "/{approvalRequestStepId}/approve'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + TASKS + "/{approvalRequestStepId}/reject'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['" + WORKFLOWS + "'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + WORKFLOWS + "'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + WORKFLOWS + "/{workflowId}'].put.responses['401']").exists())
                .andExpect(jsonPath("$.paths['" + WORKFLOWS + "/{workflowId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.components.schemas.UpdateApprovalWorkflowRequest.properties", not(hasKey("code"))))
                .andExpect(jsonPath("$.components.schemas.ApprovalRequestDetail.properties", not(hasKey("requesterUserAccountId"))));
    }
}
