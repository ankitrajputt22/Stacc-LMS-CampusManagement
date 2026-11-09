package com.stacc.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Protected endpoints refer to this scheme by name when they are documented. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI staccOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stacc API")
                        .description("API documentation for the Stacc college learning and campus management system.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The access token returned by the college login.")));
    }
}
