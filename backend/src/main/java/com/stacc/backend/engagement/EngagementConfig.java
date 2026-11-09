package com.stacc.backend.engagement;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EngagementProperties.class)
class EngagementConfig {
}
