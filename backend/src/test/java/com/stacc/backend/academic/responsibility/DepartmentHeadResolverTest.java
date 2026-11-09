package com.stacc.backend.academic.responsibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DepartmentHeadResolverTest {

    private final DepartmentHeadRepository heads = mock(DepartmentHeadRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final DepartmentHeadResolver resolver = new DepartmentHeadResolver(heads, students);

    private final Department cse = id(new Department("CSE", "Computer Science and Engineering"), 3);

    private static <T> T id(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private DepartmentHead termOf(UserAccount account) {
        DepartmentHead head = new DepartmentHead(cse, account, Instant.parse("2026-07-01T00:00:00Z"));
        when(heads.findActiveByDepartmentId(3L)).thenReturn(Optional.of(head));
        return head;
    }

    @Test
    void theHeadCountsOnlyWhileTheAccountIsActiveAndHoldsHod() {
        UserAccount hod = id(new UserAccount("hod1", "example-hash-value"), 12);
        hod.assignRole(new Role(RoleName.HOD));
        termOf(hod);
        assertThat(resolver.headOf(3L)).contains(hod);

        hod.setStatus(AccountStatus.DISABLED);
        assertThat(resolver.headOf(3L)).isEmpty();

        hod.setStatus(AccountStatus.ACTIVE);
        hod.removeRole(new Role(RoleName.HOD));
        assertThat(resolver.headOf(3L)).isEmpty();
    }

    @Test
    void aStudentsHeadIsTheHeadOfTheirProgramsDepartment() {
        UserAccount hod = id(new UserAccount("hod1", "example-hash-value"), 12);
        hod.assignRole(new Role(RoleName.HOD));
        termOf(hod);
        Program btech = new Program(cse, "BTECH-CSE", "B.Tech CSE", 8);
        AcademicSession session = new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
        when(students.findWithContextByUserAccountId(70L)).thenReturn(Optional.of(
                new StudentProfile(id(new UserAccount("student1", "example-hash-value"), 70), btech, session)));

        assertThat(resolver.headForStudent(70L)).contains(hod);
        assertThat(resolver.headForStudent(71L)).isEmpty();
    }

    @Test
    void anEndedTermIsHistoryAndCanNotBeReopened() {
        DepartmentHead head = new DepartmentHead(cse, new UserAccount("hod1", "example-hash-value"),
                Instant.parse("2026-07-01T00:00:00Z"));
        head.end(Instant.parse("2026-08-01T00:00:00Z"));
        head.end(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(head.isActive()).isFalse();
        assertThat(head.getEndedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(DepartmentHeadResolver.counts(head)).isFalse();
    }
}
