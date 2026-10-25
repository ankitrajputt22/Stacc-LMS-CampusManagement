package com.stacc.backend.support.helpdesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryService;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskAssigneeResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskMessageResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskTicketPage;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import com.stacc.backend.support.helpdesk.HelpdeskTicketQueryService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the helpdesk routes through Spring Security, with the helpdesk services as stand-ins. */
class HelpdeskControllerTest extends DatabaseFreeApiTest {

    private static final String TICKETS = "/api/helpdesk/tickets";
    private static final String ADMIN_TICKETS = "/api/helpdesk/admin/tickets";
    private static final String CATEGORIES = "/api/helpdesk/categories";
    private static final String ADMIN_CATEGORIES = "/api/helpdesk/admin/categories";
    private static final String ASSIGNEES = "/api/helpdesk/admin/assignees";

    private static final String[] OWN = {HelpdeskActor.CREATE, HelpdeskActor.VIEW_OWN, HelpdeskActor.REPLY_OWN};
    private static final String[] ALL = {HelpdeskActor.VIEW_ALL, HelpdeskActor.MANAGE, HelpdeskActor.REPLY_ALL};

    @MockitoBean
    private HelpdeskTicketCommandService commands;

    @MockitoBean
    private HelpdeskTicketQueryService queries;

    @MockitoBean
    private HelpdeskCategoryService categories;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    private static String[] with(String role, String[]... groups) {
        List<String> all = new ArrayList<>(List.of(role));
        for (String[] group : groups) {
            all.addAll(List.of(group));
        }
        return all.toArray(String[]::new);
    }

    private static String student() {
        return token(70, with("ROLE_STUDENT", OWN));
    }

    private static String admin() {
        return token(1, with("ROLE_ADMIN", OWN, ALL));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<MockHttpServletRequestBuilder> everyRoute() {
        return List.of(get(CATEGORIES), get(ADMIN_CATEGORIES),
                json(post(ADMIN_CATEGORIES), "{\"code\":\"HOSTEL\",\"name\":\"Hostel\"}"),
                json(put(ADMIN_CATEGORIES + "/3"), "{\"name\":\"Hostel\",\"active\":true}"),
                json(post(TICKETS), "{\"categoryId\":3,\"subject\":\"S\",\"description\":\"D\"}"),
                get(TICKETS + "/me"), get(TICKETS + "/42"),
                json(post(TICKETS + "/42/messages"), "{\"message\":\"Hello\"}"),
                get(ADMIN_TICKETS), get(ASSIGNEES),
                json(put(ADMIN_TICKETS + "/42/assignment"), "{\"assignedToLoginId\":\"admin1\"}"),
                json(put(ADMIN_TICKETS + "/42/priority"), "{\"priority\":\"HIGH\"}"),
                json(put(ADMIN_TICKETS + "/42/status"), "{\"status\":\"CLOSED\"}"));
    }

    private void expect(MockHttpServletRequestBuilder request, String token, HttpStatus expected) throws Exception {
        int code = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
        assertThat(code).as(request.buildRequest(context.getServletContext()).getMethod() + " "
                + request.buildRequest(context.getServletContext()).getRequestURI()).isEqualTo(expected.value());
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        for (MockHttpServletRequestBuilder request : everyRoute()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(commands, queries, categories);
    }

    @Test
    void rolesWithoutHelpdeskPermissionsOrPermissionsWithoutARoleAreForbidden() throws Exception {
        for (String denied : new String[] {
                token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW", "NOTIFICATION_VIEW"),
                token(80, "ROLE_FACULTY", "FACULTY_COURSE_VIEW"),
                token(1, "ROLE_ADMIN", "ADMIN_DASHBOARD_VIEW"),
                token(5, with("NO_ROLE", OWN, ALL))}) {
            for (MockHttpServletRequestBuilder request : everyRoute()) {
                expect(request, denied, HttpStatus.FORBIDDEN);
            }
        }
        verifyNoInteractions(commands, queries, categories);
    }

    @Test
    void requestersReachOnlyTheRequesterRoutes() throws Exception {
        for (String requester : new String[] {student(), token(80, with("ROLE_FACULTY", OWN)),
                token(1, with("ROLE_ADMIN", OWN))}) {
            expect(get(CATEGORIES), requester, HttpStatus.OK);
            expect(get(TICKETS + "/me"), requester, HttpStatus.OK);
            expect(get(TICKETS + "/42"), requester, HttpStatus.OK);
            expect(json(post(TICKETS), "{\"categoryId\":3,\"subject\":\"S\",\"description\":\"D\"}"), requester,
                    HttpStatus.CREATED);
            expect(json(post(TICKETS + "/42/messages"), "{\"message\":\"Hello\"}"), requester, HttpStatus.CREATED);

            expect(get(ADMIN_CATEGORIES), requester, HttpStatus.FORBIDDEN);
            expect(get(ADMIN_TICKETS), requester, HttpStatus.FORBIDDEN);
            expect(get(ASSIGNEES), requester, HttpStatus.FORBIDDEN);
            expect(json(put(ADMIN_TICKETS + "/42/status"), "{\"status\":\"CLOSED\"}"), requester, HttpStatus.FORBIDDEN);
        }
        verify(commands, never()).changeStatus(any(), anyLong(), any());
    }

    @Test
    void theAllPermissionsNeedTheAdminRole() throws Exception {
        String facultyWithAll = token(80, with("ROLE_FACULTY", ALL));
        for (MockHttpServletRequestBuilder request : List.of(get(ADMIN_TICKETS), get(ADMIN_CATEGORIES), get(ASSIGNEES),
                get(TICKETS + "/42"), json(post(TICKETS + "/42/messages"), "{\"message\":\"Hello\"}"),
                json(put(ADMIN_TICKETS + "/42/priority"), "{\"priority\":\"HIGH\"}"))) {
            expect(request, facultyWithAll, HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void eachAdminRouteNeedsItsOwnPermission() throws Exception {
        String viewOnly = token(1, "ROLE_ADMIN", HelpdeskActor.VIEW_ALL);
        expect(get(ADMIN_TICKETS), viewOnly, HttpStatus.OK);
        expect(get(TICKETS + "/42"), viewOnly, HttpStatus.OK);
        expect(json(post(TICKETS + "/42/messages"), "{\"message\":\"Hello\"}"), viewOnly, HttpStatus.FORBIDDEN);
        expect(json(put(ADMIN_TICKETS + "/42/assignment"), "{\"assignedToLoginId\":null}"), viewOnly,
                HttpStatus.FORBIDDEN);
        expect(get(ADMIN_CATEGORIES), viewOnly, HttpStatus.FORBIDDEN);
        expect(get(ASSIGNEES), viewOnly, HttpStatus.FORBIDDEN);

        String manageOnly = token(1, "ROLE_ADMIN", HelpdeskActor.MANAGE);
        expect(get(ADMIN_TICKETS), manageOnly, HttpStatus.FORBIDDEN);
        expect(get(ADMIN_CATEGORIES), manageOnly, HttpStatus.OK);
        expect(get(ASSIGNEES), manageOnly, HttpStatus.OK);
        expect(json(put(ADMIN_TICKETS + "/42/status"), "{\"status\":\"CLOSED\"}"), manageOnly, HttpStatus.OK);

        for (MockHttpServletRequestBuilder request : everyRoute()) {
            assertThat(mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, admin())).andReturn().getResponse()
                    .getStatus()).isBetween(200, 201);
        }
    }

    @Test
    void theActorComesFromTheTokenNeverFromTheRequest() throws Exception {
        when(queries.ownTickets(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new HelpdeskTicketPage(List.of(), 2, 5, 11, 3));

        mockMvc.perform(get(TICKETS + "/me").param("status", "OPEN").param("priority", "HIGH").param("categoryId", "3")
                        .param("page", "2").param("size", "5").param("userAccountId", "1").param("requesterId", "1")
                        .header(HttpHeaders.AUTHORIZATION, student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<HelpdeskActor> actor = ArgumentCaptor.forClass(HelpdeskActor.class);
        verify(queries).ownTickets(actor.capture(), eq(HelpdeskTicketStatus.OPEN), eq(HelpdeskTicketPriority.HIGH),
                eq(3L), eq(2), eq(5));
        assertThat(actor.getValue().accountId()).isEqualTo(70L);
        assertThat(actor.getValue().admin()).isFalse();
        assertThat(actor.getValue().viewAll()).isFalse();
    }

    @Test
    void theQueuePassesItsFiltersAndUsesThePageDefaults() throws Exception {
        mockMvc.perform(get(ADMIN_TICKETS).param("assignedToMe", "true").param("requesterLoginId", "student1")
                .header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isOk());

        ArgumentCaptor<HelpdeskActor> actor = ArgumentCaptor.forClass(HelpdeskActor.class);
        verify(queries).queue(actor.capture(), isNull(), isNull(), isNull(), eq(true), eq("student1"), eq(0), eq(20));
        assertThat(actor.getValue().accountId()).isEqualTo(1L);
        assertThat(actor.getValue().manage()).isTrue();
    }

    @Test
    void invalidRequestsAre400BeforeTheServices() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                json(post(TICKETS), "{\"categoryId\":3,\"subject\":\" \",\"description\":\"D\"}"),
                json(post(TICKETS), "{\"subject\":\"S\",\"description\":\"D\"}"),
                json(post(TICKETS), "{\"categoryId\":3,\"subject\":\"" + "x".repeat(201) + "\",\"description\":\"D\"}"),
                json(post(TICKETS), "{\"categoryId\":3,\"subject\":\"S\",\"description\":\"D\",\"priority\":\"CRITICAL\"}"),
                json(post(TICKETS + "/42/messages"), "{\"message\":\"\"}"),
                json(post(TICKETS + "/42/messages"), "{\"message\":\"" + "x".repeat(10_001) + "\"}"),
                json(put(ADMIN_TICKETS + "/42/status"), "{\"status\":\"REOPENED\"}"),
                json(put(ADMIN_TICKETS + "/42/priority"), "{}"),
                json(post(ADMIN_CATEGORIES), "{\"code\":\"\",\"name\":\"Hostel\"}"),
                json(put(ADMIN_CATEGORIES + "/3"), "{\"name\":\"Hostel\"}"),
                get(TICKETS + "/me").param("status", "WAITING"))) {
            expect(request, admin(), HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(commands, queries, categories);
    }

    @Test
    void serviceRefusalsKeepTheirStatusAndSafeMessage() throws Exception {
        when(queries.ticket(any(), eq(43L))).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Ticket not found."));
        when(commands.addMessage(any(), eq(44L), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This ticket is closed and can no longer be changed."));

        mockMvc.perform(get(TICKETS + "/43").header(HttpHeaders.AUTHORIZATION, student()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found."));
        mockMvc.perform(json(post(TICKETS + "/44/messages"), "{\"message\":\"Hello\"}")
                        .header(HttpHeaders.AUTHORIZATION, student()))
                .andExpect(status().isConflict());
    }

    @Test
    void aReplyAnswers201WithTheMessage() throws Exception {
        when(commands.addMessage(any(), eq(42L), eq("Hello")))
                .thenReturn(new HelpdeskMessageResponse(7L, "student1", "Hello", Instant.parse("2026-10-25T09:00:00Z")));

        mockMvc.perform(json(post(TICKETS + "/42/messages"), "{\"message\":\"Hello\",\"authorId\":1}")
                        .header(HttpHeaders.AUTHORIZATION, student()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorLoginId").value("student1"))
                .andExpect(jsonPath("$", not(hasKey("authorId"))));
    }

    @Test
    void theCategoryListHasNoInternalFields() throws Exception {
        when(categories.activeCategories()).thenReturn(List.of(new HelpdeskCategoryResponse(3L, "LMS", "LMS", null)));

        mockMvc.perform(get(CATEGORIES).header(HttpHeaders.AUTHORIZATION, student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LMS"))
                .andExpect(jsonPath("$[0]", not(hasKey("active"))));
    }

    @Test
    void theAssigneeListHasLoginIdsOnly() throws Exception {
        when(queries.assignees(any())).thenReturn(List.of(new HelpdeskAssigneeResponse("admin1")));

        mockMvc.perform(get(ASSIGNEES).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loginId").value("admin1"))
                .andExpect(jsonPath("$[0].length()").value(1));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['" + ASSIGNEES + "'].get.security[0].bearerAuth").exists());
    }

    @Test
    void nothingCanBeDeleted() throws Exception {
        for (String route : List.of(TICKETS + "/42", TICKETS + "/42/messages", ADMIN_TICKETS + "/42",
                ADMIN_CATEGORIES + "/3")) {
            int code = mockMvc.perform(delete(route).header(HttpHeaders.AUTHORIZATION, admin())).andReturn()
                    .getResponse().getStatus();
            assertThat(code).as(route).isIn(404, 405);
        }
        verifyNoInteractions(commands, queries, categories);
    }

    @Test
    void theRoutesAreDocumentedWithBearerSecurityAndNoDelete() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + TICKETS + "'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "/me'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "/{ticketId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "/{ticketId}'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "/{ticketId}'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['" + TICKETS + "/{ticketId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.paths['" + TICKETS + "/{ticketId}/messages'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_TICKETS + "'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_TICKETS + "/{ticketId}/status'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_TICKETS + "/{ticketId}/assignment'].put").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_TICKETS + "/{ticketId}/priority'].put").exists())
                .andExpect(jsonPath("$.paths['" + CATEGORIES + "'].get").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_CATEGORIES + "'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_CATEGORIES + "/{categoryId}'].put").exists())
                .andExpect(jsonPath("$.paths['" + ADMIN_CATEGORIES + "/{categoryId}']", not(hasKey("delete"))))
                .andExpect(jsonPath("$.components.schemas.HelpdeskTicketDetail.properties.history").exists())
                .andExpect(jsonPath("$.components.schemas.CreateHelpdeskTicketRequest.properties", not(hasKey("status"))));
    }

    @Test
    void assignedToMeIsOffUnlessAsked() throws Exception {
        mockMvc.perform(get(ADMIN_TICKETS).header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isOk());

        verify(queries).queue(any(), isNull(), isNull(), isNull(), eq(false), isNull(), eq(0), eq(20));
    }
}
