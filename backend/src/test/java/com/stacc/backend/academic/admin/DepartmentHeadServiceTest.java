package com.stacc.backend.academic.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.DepartmentHeadRecords.AssignDepartmentHead;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.DepartmentHeadRow;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.responsibility.DepartmentHead;
import com.stacc.backend.academic.responsibility.DepartmentHeadRepository;
import com.stacc.backend.academic.responsibility.DepartmentHeadStatus;
import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class DepartmentHeadServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final DepartmentHeadRepository heads = mock(DepartmentHeadRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PersonProfileRepository people = mock(PersonProfileRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final DepartmentHeadService service = new DepartmentHeadService(departments, heads, accounts, people, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Department cse = id(new Department("CSE", "Computer Science and Engineering"), 3);

    private static <T> T id(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static UserAccount account(long id, String loginId, RoleName... roles) {
        UserAccount account = id(new UserAccount(loginId, "example-hash-value"), id);
        for (RoleName role : roles) {
            account.assignRole(new Role(role));
        }
        return account;
    }

    @BeforeEach
    void setUp() {
        when(departments.findByIdForUpdate(3L)).thenReturn(Optional.of(cse));
        when(heads.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(people.findAllByUserAccountIdIn(any())).thenReturn(List.of());
    }

    @Test
    void assigningANewHeadEndsTheCurrentTermAndStartsANewOne() {
        DepartmentHead current = id(new DepartmentHead(cse, account(10, "hod0", RoleName.HOD), NOW.minusSeconds(86_400)), 1);
        when(heads.findActiveByDepartmentId(3L)).thenReturn(Optional.of(current));
        when(accounts.findById(12L)).thenReturn(Optional.of(account(12, "hod1", RoleName.FACULTY, RoleName.HOD)));

        DepartmentHeadRow row = service.assign(99L, 3L, new AssignDepartmentHead(12L));

        assertThat(current.getStatus()).isEqualTo(DepartmentHeadStatus.ENDED);
        assertThat(current.getEndedAt()).isEqualTo(NOW);
        assertThat(row.head().loginId()).isEqualTo("hod1");
        assertThat(row.head().startedAt()).isEqualTo(NOW);
        assertThat(row.head().counts()).isTrue();
        assertThat(row.head().name()).isNull();
        verify(audit).record(99L, AuditAction.DEPARTMENT_HEAD_ENDED, "DEPARTMENT", 3L, "hod0 no longer heads CSE.");
        verify(audit).record(99L, AuditAction.DEPARTMENT_HEAD_ASSIGNED, "DEPARTMENT", 3L, "hod1 now heads CSE.");
    }

    @Test
    void theSameHeadAgainChangesNothing() {
        UserAccount hod = account(12, "hod1", RoleName.HOD);
        when(heads.findActiveByDepartmentId(3L)).thenReturn(Optional.of(new DepartmentHead(cse, hod, NOW)));
        when(accounts.findById(12L)).thenReturn(Optional.of(hod));

        service.assign(99L, 3L, new AssignDepartmentHead(12L));

        verify(heads, never()).saveAndFlush(any());
        verify(audit, never()).record(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void onlyAnActiveAccountHoldingHodCanHeadAnActiveDepartment() {
        when(heads.findActiveByDepartmentId(3L)).thenReturn(Optional.empty());
        when(accounts.findById(20L)).thenReturn(Optional.of(account(20, "faculty1", RoleName.FACULTY)));
        UserAccount disabled = account(21, "hod2", RoleName.HOD);
        disabled.setStatus(AccountStatus.DISABLED);
        when(accounts.findById(21L)).thenReturn(Optional.of(disabled));

        assertRefused(() -> service.assign(99L, 3L, new AssignDepartmentHead(20L)), HttpStatus.CONFLICT,
                DepartmentHeadService.NOT_HOD);
        assertRefused(() -> service.assign(99L, 3L, new AssignDepartmentHead(21L)), HttpStatus.CONFLICT,
                DepartmentHeadService.ACCOUNT_INACTIVE);
        assertRefused(() -> service.assign(99L, 3L, new AssignDepartmentHead(22L)), HttpStatus.NOT_FOUND,
                DepartmentHeadService.ACCOUNT_MISSING);
        assertRefused(() -> service.assign(99L, 4L, new AssignDepartmentHead(20L)), HttpStatus.NOT_FOUND,
                DepartmentHeadService.DEPARTMENT_MISSING);
        cse.deactivate();
        assertRefused(() -> service.assign(99L, 3L, new AssignDepartmentHead(20L)), HttpStatus.CONFLICT,
                DepartmentHeadService.DEPARTMENT_INACTIVE);
        verify(heads, never()).saveAndFlush(any());
    }

    @Test
    void endingKeepsTheTermAndEndingNothingChangesNothing() {
        DepartmentHead current = new DepartmentHead(cse, account(12, "hod1", RoleName.HOD), NOW.minusSeconds(60));
        when(heads.findActiveByDepartmentId(3L)).thenReturn(Optional.of(current), Optional.empty());

        assertThat(service.end(99L, 3L).head()).isNull();
        assertThat(current.getStatus()).isEqualTo(DepartmentHeadStatus.ENDED);
        verify(audit).record(99L, AuditAction.DEPARTMENT_HEAD_ENDED, "DEPARTMENT", 3L, "hod1 no longer heads CSE.");

        service.end(99L, 3L);
        verify(heads).saveAndFlush(current);
        verify(audit).record(eq(99L), eq(AuditAction.DEPARTMENT_HEAD_ENDED), anyString(), anyLong(), anyString());
    }

    @Test
    void anActiveTermWhoseAccountLostTheRoleIsShownAsNotCounting() {
        DepartmentHead current = new DepartmentHead(cse, account(12, "hod1", RoleName.FACULTY), NOW);
        when(heads.findAllActive()).thenReturn(List.of(current));
        when(departments.findAllByOrderByCodeAscIdAsc()).thenReturn(List.of(cse));

        var head = service.overview().getFirst().head();

        assertThat(head.counts()).isFalse();
        assertThat(head.notCountedReason()).contains("no longer holds");
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }
}
