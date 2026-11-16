package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.ai.control.AiLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiProperties.class, AiLimitProperties.class})
class AiConfig {
}
