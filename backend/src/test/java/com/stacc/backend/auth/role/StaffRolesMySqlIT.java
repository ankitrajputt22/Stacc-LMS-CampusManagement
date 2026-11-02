package com.stacc.backend.auth.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import com.stacc.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Every role in the code has its row, and the staff roles hold only what every account needs until their work exists. */
class StaffRolesMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyRoleExistsAndStaffRolesHoldOnlyTheirOwnProfilePermissions() {
        List<String> stored = jdbc.queryForList("select name from roles", String.class);
        assertThat(stored).containsExactlyInAnyOrderElementsOf(Arrays.stream(RoleName.values()).map(Enum::name).toList());

        for (String staff : List.of("HOD", "WARDEN", "SECURITY", "MAINTENANCE", "LIBRARIAN", "PLACEMENT_OFFICER")) {
            assertThat(jdbc.queryForList("""
                    select permission.code from role_permissions link
                    join roles role on role.id = link.role_id
                    join permissions permission on permission.id = link.permission_id
                    where role.name = ?
                    """, String.class, staff))
                    .as(staff)
                    .containsExactlyInAnyOrder("PERSON_PROFILE_VIEW_OWN", "PERSON_PROFILE_EDIT_OWN");
        }
    }
}
