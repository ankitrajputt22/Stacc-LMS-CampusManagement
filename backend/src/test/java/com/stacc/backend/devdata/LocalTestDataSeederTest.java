package com.stacc.backend.devdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The checks that decide whether the local test data may be created at all. */
class LocalTestDataSeederTest {

    // A made-up value for these tests only.
    private static final String TEST_ONLY_PASSWORD = "unit-test-only-value";

    private static MockEnvironment environment(String password, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        if (password != null) {
            environment.setProperty(LocalTestDataSeeder.PASSWORD_VARIABLE, password);
        }
        return environment;
    }

    @Test
    void theSeedPasswordComesFromTheEnvironment() {
        assertThat(LocalTestDataSeeder.checkedPassword(environment(TEST_ONLY_PASSWORD, LocalTestDataSeeder.PROFILE)))
                .isEqualTo(TEST_ONLY_PASSWORD);
    }

    @Test
    void aMissingBlankOrShortPasswordStopsTheSeedWithoutRevealingIt() {
        for (String refused : new String[] {null, "", "        ", "short7!"}) {
            assertThatThrownBy(() -> LocalTestDataSeeder.checkedPassword(environment(refused, LocalTestDataSeeder.PROFILE)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(LocalTestDataSeeder.PASSWORD_VARIABLE)
                    .hasMessageNotContaining("short7!");
        }
    }

    @Test
    void theSeedNeverRunsNextToAProductionProfile() {
        for (String production : new String[] {"prod", "PRODUCTION"}) {
            assertThatThrownBy(() -> LocalTestDataSeeder.checkedPassword(
                    environment(TEST_ONLY_PASSWORD, LocalTestDataSeeder.PROFILE, production)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("production")
                    .hasMessageNotContaining(TEST_ONLY_PASSWORD);
        }
    }

    @Test
    void theDocumentedAccountsAreTheSevenTestLogins() {
        assertThat(LocalTestDataSeeder.TEST_LOGIN_IDS).containsExactly(
                "student1", "student2", "faculty1", "faculty2", "admin1", "studentfaculty1", "facultyadmin1");
    }
}
