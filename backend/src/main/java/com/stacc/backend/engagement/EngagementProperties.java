package com.stacc.backend.engagement;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The college's own time zone, in which facility opening hours and closed days are read. */
@ConfigurationProperties(prefix = "stacc.college")
public record EngagementProperties(String timeZone) {

    public EngagementProperties {
        try {
            ZoneId.of(timeZone);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("COLLEGE_TIME_ZONE must be an IANA time zone such as Asia/Kolkata.");
        }
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
