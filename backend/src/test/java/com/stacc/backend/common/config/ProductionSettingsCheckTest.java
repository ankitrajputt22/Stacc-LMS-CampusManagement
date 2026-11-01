package com.stacc.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSettingsCheckTest {

    /** Settings a real deployment could start with. */
    private static MockEnvironment safe() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.username", "stacc")
                .withProperty("stacc.storage.access-key", "storage-user")
                .withProperty("stacc.storage.secret-key", "storage-secret-value")
                .withProperty("stacc.cors.allowed-origins", "")
                .withProperty("stacc.ai.enabled", "false");
        environment.setActiveProfiles("prod");
        return environment;
    }

    @Test
    void safeSettingsStart() {
        assertThat(ProductionSettingsCheck.problems(safe())).isEmpty();
        assertThat(ProductionSettingsCheck.problems(safe().withProperty("stacc.cors.allowed-origins",
                "https://gostacc.com, https://admin.gostacc.com"))).isEmpty();
        assertThat(ProductionSettingsCheck.problems(safe().withProperty("stacc.ai.enabled", "true")
                .withProperty("stacc.ai.internal-token", "x".repeat(32)))).isEmpty();
    }

    @Test
    void laptopSettingsAreRefused() {
        MockEnvironment environment = safe()
                .withProperty("spring.datasource.username", "ROOT")
                .withProperty("stacc.storage.secret-key", " ")
                .withProperty("STACC_TEST_PASSWORD", "demo-password")
                .withProperty("stacc.cors.allowed-origins", "http://localhost:5173")
                .withProperty("stacc.ai.enabled", "true")
                .withProperty("stacc.ai.internal-token", "short");
        environment.setActiveProfiles("prod", "dev-seed");

        assertThat(ProductionSettingsCheck.problems(environment)).hasSize(6);
    }

    @Test
    void originsMustBeHttpsAndReal() {
        for (String origin : new String[] {"http://gostacc.com", "https://localhost", "https://127.0.0.1:8443",
            "https://app.localhost", "not an origin"}) {
            assertThat(ProductionSettingsCheck.problems(safe().withProperty("stacc.cors.allowed-origins", origin)))
                    .as(origin).hasSize(1);
        }
    }

    @Test
    void theRefusalListsProblemsButNeverValues() {
        MockEnvironment environment = safe()
                .withProperty("spring.datasource.username", "root")
                .withProperty("STACC_TEST_PASSWORD", "very-secret-demo-password");

        assertThatThrownBy(() -> new ProductionSettingsCheck(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MYSQL_USER")
                .hasMessageContaining("STACC_TEST_PASSWORD")
                .hasMessageNotContaining("very-secret-demo-password");
    }
}
