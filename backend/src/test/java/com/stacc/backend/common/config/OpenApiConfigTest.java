package com.stacc.backend.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void exposesStaccApiInformation() {
        OpenAPI openApi = new OpenApiConfig().staccOpenApi();

        assertEquals("Stacc API", openApi.getInfo().getTitle());
        assertEquals(
                "API documentation for the Stacc college learning and campus management system.",
                openApi.getInfo().getDescription());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}
