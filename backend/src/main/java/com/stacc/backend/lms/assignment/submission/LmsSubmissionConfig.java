package com.stacc.backend.lms.assignment.submission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LmsSubmissionProperties.class)
public class LmsSubmissionConfig {
}
