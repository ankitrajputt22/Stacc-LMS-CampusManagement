package com.stacc.backend.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production deployment ({@code prod} profile) with settings that are fine on a laptop but unsafe
 * for real users. Each setting is also checked on its own elsewhere (a weak JWT secret or a wildcard origin never
 * starts anywhere); this adds the combinations that only matter in production. Every problem is listed at once, and
 * no message ever contains a setting's value.
 */
@Component
@Profile(ProductionSettingsCheck.PROFILE)
public class ProductionSettingsCheck {

    public static final String PROFILE = "prod";
    static final int MIN_AI_TOKEN_LENGTH = 32;

    public ProductionSettingsCheck(Environment environment) {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Stacc will not start in production with these settings:\n- "
                    + String.join("\n- ", problems) + "\nSee docs/configuration.md.");
        }
    }

    static List<String> problems(Environment environment) {
        List<String> problems = new ArrayList<>();
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch("dev-seed"::equalsIgnoreCase)) {
            problems.add("The dev-seed profile creates test accounts and must not be active.");
        }
        if (!blank(environment.getProperty("STACC_TEST_PASSWORD"))) {
            problems.add("STACC_TEST_PASSWORD is for local test data only and must not be set.");
        }
        if ("root".equalsIgnoreCase(trimmed(environment.getProperty("spring.datasource.username")))) {
            problems.add("MYSQL_USER must be the application's own database account, not root.");
        }
        if (blank(environment.getProperty("stacc.storage.access-key"))
                || blank(environment.getProperty("stacc.storage.secret-key"))) {
            problems.add("STACC_STORAGE_ACCESS_KEY and STACC_STORAGE_SECRET_KEY must be set.");
        }
        for (String origin : trimmed(environment.getProperty("stacc.cors.allowed-origins")).split(",")) {
            if (!origin.isBlank() && !isPublicHttpsOrigin(origin.strip())) {
                problems.add("FRONTEND_ORIGIN may list only https origins of real hosts, never http or localhost.");
                break;
            }
        }
        if (!Boolean.parseBoolean(trimmed(environment.getProperty("stacc.session.secure-cookie")))) {
            problems.add("STACC_SESSION_SECURE_COOKIE must be true: production is served over HTTPS.");
        }
        if (Boolean.parseBoolean(trimmed(environment.getProperty("stacc.ai.enabled")))
                && trimmed(environment.getProperty("stacc.ai.internal-token")).length() < MIN_AI_TOKEN_LENGTH) {
            problems.add("AI_ENABLED is true, so STACC_AI_INTERNAL_TOKEN must be at least "
                    + MIN_AI_TOKEN_LENGTH + " characters.");
        }
        if (Boolean.parseBoolean(trimmed(environment.getProperty("stacc.mail.enabled")))
                && !trimmed(environment.getProperty("stacc.mail.link-base-url")).startsWith("https://")) {
            problems.add("MAIL_ENABLED is true, so MAIL_LINK_BASE_URL must be the https address of the frontend.");
        }
        return problems;
    }

    private static boolean isPublicHttpsOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && !host.isEmpty() && !host.equals("localhost")
                    && !host.endsWith(".localhost") && !host.equals("127.0.0.1") && !host.equals("[::1]");
        } catch (java.net.URISyntaxException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.strip();
    }
}
