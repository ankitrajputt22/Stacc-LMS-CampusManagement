package com.stacc.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.common.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

class RequestIdAndHealthTest extends DatabaseFreeApiTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @MockitoBean
    private ObjectStorageService storage;

    @Test
    void everyAnswerCarriesARequestIdAndErrorsRepeatIt() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/lms/my-courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)))
                .andReturn();

        String sent = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + sent + "\"");
    }

    @Test
    void aWellFormedIncomingIdIsKeptSoProxyAndBackendLogsMatch() throws Exception {
        mockMvc.perform(get("/does-not-exist").header(RequestIdFilter.HEADER, "a1b2c3d4e5f60718"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RequestIdFilter.HEADER, "a1b2c3d4e5f60718"))
                .andExpect(jsonPath("$.requestId").value("a1b2c3d4e5f60718"));
    }

    @Test
    void anythingElseIsReplacedByANewId() throws Exception {
        for (String incoming : new String[] {"short", "has spaces in it", "x".repeat(65), "line\nbreak-123456"}) {
            mockMvc.perform(get("/does-not-exist").header(RequestIdFilter.HEADER, incoming))
                    .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
        }
    }

    @Test
    void healthShowsEachPartsStatusWithoutDetails() throws Exception {
        when(storage.isReachable()).thenReturn(false);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.storage.status").value("DOWN"))
                .andExpect(jsonPath("$.components.aiService.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.storage.details").doesNotExist());
    }

    @Test
    void livenessAndReadinessDoNotDependOnStorageOrAi() throws Exception {
        when(storage.isReachable()).thenReturn(false);

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void onlyTheHealthEndpointIsExposed() throws Exception {
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/info",
            "/actuator/metrics", "/actuator/loggers", "/actuator/heapdump"}) {
            mockMvc.perform(get(endpoint)).andExpect(status().isNotFound());
        }
    }
}
