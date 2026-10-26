package com.stacc.backend.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.search.GlobalSearchService;
import com.stacc.backend.search.SearchActor;
import com.stacc.backend.search.SearchResponses.GlobalSearchResponse;
import com.stacc.backend.search.SearchResponses.SearchResultPage;
import com.stacc.backend.search.SearchResultType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Calls the search routes through Spring Security, with the search service as a stand-in. */
class SearchControllerTest extends DatabaseFreeApiTest {

    @MockitoBean
    private GlobalSearchService search;

    private static String token(long accountId, String... authorities) {
        return "Bearer " + TestTokens.valid("ACC-TEST-" + accountId, accountId, authorities);
    }

    // Fresh builders every time: a builder keeps the headers of earlier calls.
    private static List<MockHttpServletRequestBuilder> routes() {
        return List.of(get("/api/search").param("q", "data"),
                get("/api/search/results").param("q", "data").param("type", "COURSE"));
    }

    private int statusOf(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andReturn().getResponse().getStatus();
    }

    @Test
    void bothRoutesNeedATokenAndGlobalSearchUseWithACollegeRole() throws Exception {
        when(search.preview(any(), any())).thenReturn(new GlobalSearchResponse("data", List.of()));
        when(search.results(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new SearchResultPage("data", SearchResultType.COURSE, "Courses", List.of(), 0, 20, 0, 0));
        for (MockHttpServletRequestBuilder route : routes()) {
            mockMvc.perform(route).andExpect(status().isUnauthorized());
        }
        for (String role : new String[] {"ROLE_STUDENT", "ROLE_FACULTY", "ROLE_ADMIN"}) {
            for (MockHttpServletRequestBuilder route : routes()) {
                assertThat(statusOf(route, token(70, role, "GLOBAL_SEARCH_USE"))).isEqualTo(200);
            }
        }
        for (String denied : new String[] {token(70, "ROLE_STUDENT", "LMS_COURSE_VIEW"), token(70, "GLOBAL_SEARCH_USE")}) {
            for (MockHttpServletRequestBuilder route : routes()) {
                assertThat(statusOf(route, denied)).isEqualTo(403);
            }
        }
    }

    @Test
    void theActorAlwaysComesFromTheTokenNeverTheRequest() throws Exception {
        when(search.preview(any(), any())).thenReturn(new GlobalSearchResponse("data", List.of()));

        mockMvc.perform(get("/api/search").param("q", "data").param("accountId", "1").param("userAccountId", "1")
                        .header(HttpHeaders.AUTHORIZATION, token(70, "ROLE_STUDENT", "ROLE_FACULTY", "GLOBAL_SEARCH_USE",
                                "LMS_COURSE_VIEW")))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchActor> actor = ArgumentCaptor.forClass(SearchActor.class);
        verify(search).preview(actor.capture(), eq("data"));
        assertThat(actor.getValue().accountId()).isEqualTo(70L);
        assertThat(actor.getValue().roles()).containsExactlyInAnyOrder("STUDENT", "FACULTY");
        assertThat(actor.getValue().authorities()).contains("GLOBAL_SEARCH_USE", "LMS_COURSE_VIEW")
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    void theTypeAndPagingArePassedOnAndAnUnknownTypeIs400() throws Exception {
        String student = token(70, "ROLE_STUDENT", "GLOBAL_SEARCH_USE");
        when(search.results(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new SearchResultPage("data", SearchResultType.ASSIGNMENT, "Assignments", List.of(), 2, 50, 0, 0));

        mockMvc.perform(get("/api/search/results").param("q", "data").param("type", "ASSIGNMENT").param("page", "2")
                .param("size", "50").header(HttpHeaders.AUTHORIZATION, student)).andExpect(status().isOk());
        verify(search).results(any(), eq("data"), eq(SearchResultType.ASSIGNMENT), eq(2), eq(50));

        assertThat(statusOf(get("/api/search/results").param("q", "data").param("type", "USER"), student)).isEqualTo(400);
        assertThat(statusOf(get("/api/search/results").param("q", "data").param("type", "COURSE").param("page", "x"),
                student)).isEqualTo(400);
    }

    @Test
    void validationRefusalsUseTheSharedErrorFormat() throws Exception {
        when(search.preview(any(), anyString())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST,
                "Enter at least 2 characters to search."));
        mockMvc.perform(get("/api/search").param("q", "a")
                        .header(HttpHeaders.AUTHORIZATION, token(70, "ROLE_STUDENT", "GLOBAL_SEARCH_USE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter at least 2 characters to search."));
    }

    @Test
    void thereIsNoSuggestionHistoryOrWriteRoute() throws Exception {
        String student = token(70, "ROLE_STUDENT", "GLOBAL_SEARCH_USE");
        for (MockHttpServletRequestBuilder request : List.of(get("/api/search/suggestions").param("q", "da"),
                get("/api/search/history"), post("/api/search").param("q", "data"), get("/api/search/users").param("q", "st"))) {
            assertThat(statusOf(request, student)).isIn(400, 404, 405);
        }
        verifyNoInteractions(search);
    }

    @Test
    void theRoutesAreDocumentedWithBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/search'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/search'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/search'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/search/results'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/search/results'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/search/results'].get.parameters[?(@.name == 'type')]").exists())
                .andExpect(jsonPath("$.components.schemas.SearchResult.properties", not(hasKey("id"))))
                .andExpect(jsonPath("$.components.schemas.SearchResult.properties", not(hasKey("accountId"))));
    }
}
