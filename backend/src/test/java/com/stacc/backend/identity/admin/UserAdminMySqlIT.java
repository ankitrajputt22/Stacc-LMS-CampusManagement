package com.stacc.backend.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserDetail;
import com.stacc.backend.identity.admin.UserAdminRecords.UserPage;
import com.stacc.backend.identity.admin.UserAdminRecords.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Admin user management on MySQL: the search query over the demo accounts, and creating one new student account. */
class UserAdminMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private UserAdminService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void searchingFiltersByRoleStatusAndText() {
        UserPage students = users.list(RoleName.STUDENT, AccountStatus.ACTIVE, null, 0, 100);
        assertThat(students.items()).extracting(UserSummary::loginId).contains("student1", "student2")
                .doesNotContain("admin1");

        UserPage byName = users.list(null, null, "AARAV", 0, 100);
        assertThat(byName.items()).extracting(UserSummary::loginId).containsExactly("student1");

        assertThat(users.list(null, null, "%", 0, 100).items()).isEmpty();
    }

    @Test
    void aNewStudentAccountIsCreatedWithItsProfilesAndMustChangeItsPassword() {
        Long programId = jdbc.queryForObject("select id from programs where code = 'BTECH-CSE'", Long.class);
        Long sessionId = jdbc.queryForObject(
                "select id from academic_sessions where is_current = true", Long.class);

        UserDetail created = users.create(new UserCreate("ITUSER01", "Integration Student", "it.user01@college.example",
                Set.of(RoleName.STUDENT), "Quiet-Harbor-2091", new StudentProfileCreate(programId, sessionId, "itroll01"),
                null));

        assertThat(created.passwordChangeRequired()).isTrue();
        assertThat(created.temporaryPasswordExpiresAt()).isNotNull();
        assertThat(created.studentProfile().rollNumber()).isEqualTo("ITROLL01");
        assertThat(jdbc.queryForObject("select count(*) from person_profiles where email = 'it.user01@college.example'",
                Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> users.create(new UserCreate("ITUSER01", "Again", null, Set.of(RoleName.ADMIN),
                "Quiet-Harbor-2091", null, null))).isInstanceOf(ApiException.class);
    }
}
