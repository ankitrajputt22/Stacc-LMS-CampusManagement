package com.stacc.backend.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.DatabaseFreeApiTest;
import org.junit.jupiter.api.Test;

/** Starts the application without the seed profile, as every normal run does. */
class LocalTestDataProfileTest extends DatabaseFreeApiTest {

    @Test
    void withoutTheSeedProfileNoSeederExists() {
        assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain(LocalTestDataSeeder.PROFILE);
        assertThat(context.getBeanNamesForType(LocalTestDataSeeder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(LocalTestDataSeeder.Repositories.class)).isEmpty();
        assertThat(context.getBeanNamesForType(LocalTestDataSeeder.Services.class)).isEmpty();
    }
}
