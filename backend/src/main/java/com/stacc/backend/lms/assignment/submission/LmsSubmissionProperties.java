package com.stacc.backend.lms.assignment.submission;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stacc.submissions")
public record LmsSubmissionProperties(int maxFiles) {

    public LmsSubmissionProperties {
        if (maxFiles < 1 || maxFiles > 10) {
            throw new IllegalArgumentException("stacc.submissions.max-files must be between 1 and 10");
        }
    }
}
