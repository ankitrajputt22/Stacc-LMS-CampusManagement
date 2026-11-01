package com.stacc.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.devdata.LocalTestDataSeeder;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The schema on a real, initially empty MySQL server: every migration applies from the first one, the entities match
 * the tables (the context only starts when Hibernate's validation passes), and the demo data can be seeded again
 * without creating anything twice.
 */
class DatabaseSchemaMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalTestDataSeeder seeder;

    @Test
    void everyMigrationAppliesToAnEmptyDatabase() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(files).isNotEmpty();
        assertThat(applied).hasSameSizeAs(files);
        assertThat(applied).allSatisfy(migration -> assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();
        // Versions are 1, 2, 3, … with no gaps, so a migration never goes missing unnoticed.
        assertThat(Arrays.stream(applied).map(migration -> migration.getVersion().getVersion()))
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, files.length)
                        .mapToObj(String::valueOf).toList());
    }

    @Test
    void theDatabaseUsesFullUnicode() {
        // Names and messages may contain any language or emoji, which MySQL only stores with utf8mb4.
        String charset = jdbc.queryForObject(
                "select default_character_set_name from information_schema.schemata where schema_name = database()",
                String.class);
        assertThat(charset).isEqualTo("utf8mb4");
    }

    @Test
    void seedingAgainCreatesNothing() throws Exception {
        Map<String, Long> before = rowCounts();

        seeder.run(new DefaultApplicationArguments());

        assertThat(rowCounts()).isEqualTo(before);
        // The demo data is really there, so the other MySQL tests have a college to work with.
        assertThat(before.get("user_accounts")).isGreaterThanOrEqualTo(7);
        assertThat(before.get("hostel_beds")).isGreaterThanOrEqualTo(4);
    }

    /** The number of rows in every application table, by table name. */
    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = database() and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'
                order by table_name
                """, String.class)) {
            counts.put(table, jdbc.queryForObject("select count(*) from `" + table + "`", Long.class));
        }
        return counts;
    }
}
