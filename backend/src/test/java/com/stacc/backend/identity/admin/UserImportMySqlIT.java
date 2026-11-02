package com.stacc.backend.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.stacc.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/** A CSV import on MySQL: every account, person, and profile is created together, with the temporary password state. */
class UserImportMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private UserImportService imports;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void validRowsCreateAccountsWithProfilesThatMustChangeTheirPassword() {
        String session = jdbc.queryForObject("select code from academic_sessions where is_current = true", String.class);
        String csv = "login_id,full_name,email,roles,department,program,admission_session,roll_number\n"
                + "ITIMP01,Import Student,it.imp01@college.example,STUDENT,CSE,BTECH-CSE," + session + ",ITIMPROLL1\n"
                + "ITIMP02,Import Teacher,,FACULTY,CSE,,,\n";

        UserImportService.ImportResult result = imports.importCsv(null,
                new MockMultipartFile("file", "people.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
                "Calm-Orchard-5512", false);

        assertThat(result.created()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from user_accounts where login_id in ('ITIMP01', 'ITIMP02') "
                + "and password_change_required = true", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where roll_number = 'ITIMPROLL1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from faculty_profiles profile join user_accounts account "
                + "on account.id = profile.user_account_id where account.login_id = 'ITIMP02'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'USERS_IMPORTED'",
                Integer.class)).isGreaterThanOrEqualTo(1);

        UserImportService.ImportResult again = imports.importCsv(null,
                new MockMultipartFile("file", "people.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
                "Calm-Orchard-5512", false);
        assertThat(again.created()).isZero();
        assertThat(again.rows()).allSatisfy(row ->
                assertThat(row.errors()).contains("An account with this login ID already exists."));
    }
}
