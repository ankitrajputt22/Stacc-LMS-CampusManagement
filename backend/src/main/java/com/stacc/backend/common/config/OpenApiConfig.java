package com.stacc.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI staccOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Stacc API")
                .description("API documentation for the Stacc college learning and campus management system.")
                .version("v1"));
    }
}
