package com.stacc.backend;

import com.stacc.backend.devdata.LocalTestDataSeeder;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base for tests that need a real MySQL server: migrations, constraints, row locks, and anything else a test without a
 * database cannot show. The classes are named {@code *MySqlIT} and run in {@code mvn verify}, not in {@code mvn test}.
 *
 * <p>One throwaway MySQL 8.4 container (the version {@code compose.yaml} uses) serves every such test class in the run,
 * so the Spring context is built once. It starts empty: Flyway builds the whole schema from the first migration, and
 * Hibernate then checks that every entity matches it ({@code ddl-auto=validate}). The {@code dev-seed} profile fills it
 * with the documented demo data through the real feature services, so tests start from a realistic college. Tests share
 * that data, so each one uses records no other test changes. The accounts get a random password for this run only.
 *
 * <p>Without Docker these tests are skipped, not failed; CI always has Docker, so there they always run. Nothing here
 * touches the developer's own {@code stacc} database or Docker project, and the container is removed after the run.
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "stacc.jwt.secret=" + TestSecrets.JWT_SECRET,
    // An address where nothing listens, so no test ever reaches a real file store; the seeder skips its one file.
    "stacc.storage.endpoint=http://127.0.0.1:1"
})
@ActiveProfiles(LocalTestDataSeeder.PROFILE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class MySqlIntegrationTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    /** A fresh random password for the demo accounts, used only inside this test run. */
    private static final String ACCOUNT_PASSWORD = "it-" + UUID.randomUUID();

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        // Started here, not in a static block, so a run without Docker is skipped before anything tries to start it.
        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("STACC_TEST_PASSWORD", () -> ACCOUNT_PASSWORD);
    }
}
