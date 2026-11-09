package com.stacc.backend.academic.responsibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.AssignDepartmentHead;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.DepartmentHeadRow;
import com.stacc.backend.academic.admin.DepartmentHeadService;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Department heads on MySQL: the seeded head of CSE, and two admins setting different heads of one department at the
 * same moment, which must leave exactly one active term. Uses its own department and accounts, which no other test
 * touches.
 */
class DepartmentHeadMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private DepartmentHeadService service;

    @Autowired
    private DepartmentHeadResolver resolver;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theDemoHeadOfCseIsResolvedForItsStudents() {
        Long cse = departments.findByCode("CSE").orElseThrow().getId();
        Long hod1 = accounts.findByLoginId("hod1").orElseThrow().getId();
        Long student1 = accounts.findByLoginId("student1").orElseThrow().getId();

        assertThat(resolver.headOf(cse)).map(UserAccount::getId).contains(hod1);
        assertThat(resolver.headForStudent(student1)).map(UserAccount::getId).contains(hod1);
    }

    @Test
    void twoHeadsSetAtOnceLeaveOneActiveTerm() throws Exception {
        Long department = departments.save(new Department("ITHOD", "Head Test Department")).getId();
        Long first = hodAccount("ITHOD01");
        Long second = hodAccount("ITHOD02");
        Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();

        List<Outcome<DepartmentHeadRow>> outcomes = Concurrently.run(
                () -> service.assign(admin, department, new AssignDepartmentHead(first)),
                () -> service.assign(admin, department, new AssignDepartmentHead(second)));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from department_heads where department_id = ? and status = 'ACTIVE'",
                Integer.class, department)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from department_heads where department_id = ? and status = 'ENDED'",
                Integer.class, department)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where target_type = 'DEPARTMENT' and target_id = ?",
                Integer.class, department)).isEqualTo(3);

        service.end(admin, department);
        assertThat(resolver.headOf(department)).isEmpty();
        assertThat(service.history(department)).hasSize(2).allMatch(term -> term.endedAt() != null);
    }

    private Long hodAccount(String loginId) {
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        account.assignRole(roles.findByName(RoleName.HOD).orElseThrow());
        return accounts.save(account).getId();
    }
}
