package com.stacc.backend;

import com.stacc.backend.devdata.LocalTestDataSeeder;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "stacc.jwt.secret=" + TestSecrets.JWT_SECRET,
    "stacc.storage.endpoint=http://127.0.0.1:1"
})
@ActiveProfiles(LocalTestDataSeeder.PROFILE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class MySqlIntegrationTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private static final String ACCOUNT_PASSWORD = "it-" + UUID.randomUUID();

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MYSQL.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("STACC_TEST_PASSWORD", () -> ACCOUNT_PASSWORD);
    }
}
