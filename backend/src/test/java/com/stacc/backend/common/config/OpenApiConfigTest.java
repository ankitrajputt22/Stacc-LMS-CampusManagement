package com.stacc.backend.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    @Test
    void describesBearerTokensWithoutRequiringThemEverywhere() {
        OpenAPI openApi = new OpenApiConfig().staccOpenApi();
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");

        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertNull(openApi.getSecurity());
    }
}
