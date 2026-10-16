package com.stacc.backend.lms.assignment.submission;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for assignment submissions. Each file is also held to the study-material file size limit,
 * {@code stacc.materials.max-file-size}, so both features accept the same files.
 *
 * @param maxFiles the most files one attempt may have
 */
@ConfigurationProperties(prefix = "stacc.submissions")
public record LmsSubmissionProperties(int maxFiles) {

    public LmsSubmissionProperties {
        if (maxFiles < 1 || maxFiles > 10) {
            throw new IllegalArgumentException("stacc.submissions.max-files must be between 1 and 10");
        }
    }
}
