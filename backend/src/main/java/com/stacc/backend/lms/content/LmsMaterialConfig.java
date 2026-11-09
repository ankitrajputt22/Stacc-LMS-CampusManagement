package com.stacc.backend.lms.content;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LmsMaterialProperties.class)
public class LmsMaterialConfig {
}
