package com.stacc.backend.auth.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.stacc.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every role in the code has its row, and the staff roles hold only what every account needs plus exactly the
 * permissions their features have given them so far (approval steps for HOD, support team work for the support roles).
 */
class StaffRolesMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyRoleExistsAndStaffRolesHoldOnlyTheirOwnProfilePermissions() {
        List<String> stored = jdbc.queryForList("select name from roles", String.class);
        assertThat(stored).containsExactlyInAnyOrderElementsOf(Arrays.stream(RoleName.values()).map(Enum::name).toList());

        Map<String, List<String>> granted = Map.of(
                "HOD", List.of("APPROVAL_TASK_VIEW", "APPROVAL_TASK_DECIDE"),
                "WARDEN", List.of("HELPDESK_TEAM_WORK", "NOTIFICATION_VIEW", "HOSTEL_WARDEN_VIEW", "APPROVAL_TASK_VIEW", "APPROVAL_TASK_DECIDE"),
                "MAINTENANCE", List.of("HELPDESK_TEAM_WORK", "NOTIFICATION_VIEW"),
                "LIBRARIAN", List.of("HELPDESK_TEAM_WORK", "NOTIFICATION_VIEW"),
                "SECURITY", List.of("GATE_PASS_VERIFY"),
                "PLACEMENT_OFFICER", List.of());
        granted.forEach((staff, extra) -> {
            List<String> expected = new java.util.ArrayList<>(List.of("PERSON_PROFILE_VIEW_OWN", "PERSON_PROFILE_EDIT_OWN"));
            expected.addAll(extra);
            assertThat(jdbc.queryForList("""
                    select permission.code from role_permissions link
                    join roles role on role.id = link.role_id
                    join permissions permission on permission.id = link.permission_id
                    where role.name = ?
                    """, String.class, staff))
                    .as(staff)
                    .containsExactlyInAnyOrderElementsOf(expected);
        });
    }
}
