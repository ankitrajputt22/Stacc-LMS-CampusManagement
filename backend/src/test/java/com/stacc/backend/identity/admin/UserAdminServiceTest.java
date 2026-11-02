package com.stacc.backend.identity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.password.PasswordService;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.admin.UserAdminRecords.DetailsUpdate;
import com.stacc.backend.identity.admin.UserAdminRecords.FacultyProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserDetail;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserAdminServiceTest {

    private static final String TEMPORARY = "Bright-Lantern-2047";
    private static final long ADMIN = 1L;

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PersonProfileRepository people = mock(PersonProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final FacultyProfileRepository faculty = mock(FacultyProfileRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final SessionService userSessions = mock(SessionService.class);
    private final com.stacc.backend.audit.AuditService audit = mock(com.stacc.backend.audit.AuditService.class);
    private final UserAdminService service = new UserAdminService(accounts, mock(AccountSearchRepository.class), roles,
            people, students, faculty, programs, sessions, departments, encoder, passwords, userSessions, audit);

    private final Department cse = withId(new Department("CSE", "Computer Science"), 3);
    private final Program btech = withId(new Program(cse, "BTECH", "B.Tech.", 8), 4);
    private final AcademicSession session = withId(new AcademicSession("2026-27",
            LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)), 2);

    @BeforeEach
    void setUp() {
        for (RoleName name : RoleName.values()) {
            Role role = mock(Role.class);
            when(role.getName()).thenReturn(name);
            when(roles.findByName(name)).thenReturn(Optional.of(role));
        }
        when(encoder.encode(anyString())).thenReturn("{test}hash");
        when(accounts.saveAndFlush(any(UserAccount.class))).thenAnswer(call -> {
            UserAccount saved = call.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 57L);
            }
            return saved;
        });
        when(people.saveAndFlush(any(PersonProfile.class))).thenAnswer(call -> call.getArgument(0));
        when(students.saveAndFlush(any(StudentProfile.class))).thenAnswer(call -> call.getArgument(0));
        when(faculty.saveAndFlush(any(FacultyProfile.class))).thenAnswer(call -> call.getArgument(0));
        when(programs.findWithDepartmentById(4L)).thenReturn(Optional.of(btech));
        when(sessions.findById(2L)).thenReturn(Optional.of(session));
        when(departments.findById(3L)).thenReturn(Optional.of(cse));
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        if (message != null) {
            assertEquals(message, refused.getMessage());
        }
    }

    private UserAccount stored(long id, String loginId, RoleName... held) {
        UserAccount account = withId(new UserAccount(loginId, "{test}hash"), id);
        for (RoleName name : held) {
            account.assignRole(roles.findByName(name).orElseThrow());
        }
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void aStudentAccountIsCreatedWithItsPersonAndStudentProfileAndATemporaryPassword() {
        UserCreate request = new UserCreate(" 25CSE104 ", "Riya Menon", "riya@college.example", Set.of(RoleName.STUDENT),
                TEMPORARY, new StudentProfileCreate(4L, 2L, "25cse104"), null);

        UserDetail created = service.create(ADMIN, request);

        assertEquals("25CSE104", created.loginId());
        assertEquals(List.of("STUDENT"), created.roles());
        verify(people).saveAndFlush(any(PersonProfile.class));
        verify(students).saveAndFlush(any(StudentProfile.class));
        verify(faculty, never()).saveAndFlush(any());
        verify(passwords).issueTemporaryPassword(57L, TEMPORARY, null);
        verify(audit).record(ADMIN, com.stacc.backend.audit.AuditAction.USER_CREATED, "USER_ACCOUNT", 57L,
                "Created 25CSE104 with roles STUDENT.");
        verify(audit).record(ADMIN, com.stacc.backend.audit.AuditAction.STUDENT_PROFILE_CREATED, "USER_ACCOUNT", 57L,
                "Program BTECH, admitted 2026-27.");
    }

    @Test
    void aFacultyAndAdminAccountGetsAFacultyProfileAndItsRolesInOrder() {
        UserDetail created = service.create(ADMIN, new UserCreate("EMP204", "Kiran Das", null,
                Set.of(RoleName.ADMIN, RoleName.FACULTY), TEMPORARY, null, new FacultyProfileCreate(3L)));

        assertEquals(List.of("FACULTY", "ADMIN"), created.roles());
        verify(faculty).saveAndFlush(any(FacultyProfile.class));
        verify(students, never()).saveAndFlush(any());
    }

    @Test
    void creationIsRefusedForTakenIdsMissingProfilesAndWeakPasswords() {
        when(accounts.existsByLoginId("student1")).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, UserAdminService.LOGIN_TAKEN, () -> service.create(ADMIN, new UserCreate("student1",
                "A B", null, Set.of(RoleName.ADMIN), TEMPORARY, null, null)));

        when(people.existsByEmail("taken@college.example")).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, UserAdminService.EMAIL_TAKEN, () -> service.create(ADMIN, new UserCreate("NEW1",
                "A B", "Taken@College.example", Set.of(RoleName.ADMIN), TEMPORARY, null, null)));

        assertRefused(HttpStatus.BAD_REQUEST, UserAdminService.STUDENT_PROFILE_NEEDED, () -> service.create(ADMIN, 
                new UserCreate("NEW2", "A B", null, Set.of(RoleName.STUDENT), TEMPORARY, null, null)));
        assertRefused(HttpStatus.BAD_REQUEST, UserAdminService.FACULTY_PROFILE_NEEDED, () -> service.create(ADMIN, 
                new UserCreate("NEW3", "A B", null, Set.of(RoleName.FACULTY), TEMPORARY, null, null)));
        assertRefused(HttpStatus.BAD_REQUEST, null, () -> service.create(ADMIN, 
                new UserCreate("NEW4", "A B", null, Set.of(RoleName.ADMIN), "password123", null, null)));

        verify(accounts, never()).saveAndFlush(any());
        verify(passwords, never()).issueTemporaryPassword(anyLong(), anyString(), any());
    }

    @Test
    void aStudentInAnInactiveProgramIsRefused() {
        btech.deactivate();

        assertRefused(HttpStatus.CONFLICT, UserAdminService.PROGRAM_INACTIVE, () -> service.create(ADMIN, new UserCreate("NEW5",
                "A B", null, Set.of(RoleName.STUDENT), TEMPORARY, new StudentProfileCreate(4L, 2L, null), null)));
    }

    @Test
    void disablingEndsEverySessionAndAnAdminCannotDisableThemselves() {
        UserAccount account = stored(57L, "25CSE104", RoleName.STUDENT);
        stored(ADMIN, "admin1", RoleName.ADMIN);

        assertEquals("DISABLED", service.updateStatus(ADMIN, 57L, AccountStatus.DISABLED).status());
        verify(userSessions).endAll(57L, SessionEndReason.ENDED_BY_COLLEGE);
        assertEquals(AccountStatus.DISABLED, account.getStatus());
        verify(audit).record(ADMIN, com.stacc.backend.audit.AuditAction.USER_STATUS_CHANGED, "USER_ACCOUNT", 57L,
                "ACTIVE to DISABLED.");

        assertRefused(HttpStatus.CONFLICT, UserAdminService.SELF_DISABLE,
                () -> service.updateStatus(ADMIN, ADMIN, AccountStatus.DISABLED));
        assertEquals("ACTIVE", service.updateStatus(ADMIN, 57L, AccountStatus.ACTIVE).status());
    }

    @Test
    void changingRolesEndsSessionsKeepsTheSameSetAsANoOpAndProtectsTheOwnAdminRole() {
        UserAccount account = stored(57L, "EMP204", RoleName.FACULTY);
        stored(ADMIN, "admin1", RoleName.ADMIN, RoleName.FACULTY);

        assertEquals(List.of("FACULTY"), service.updateRoles(ADMIN, 57L, Set.of(RoleName.FACULTY)).roles());
        verify(userSessions, never()).endAll(anyLong(), any());

        assertEquals(List.of("FACULTY", "ADMIN"),
                service.updateRoles(ADMIN, 57L, Set.of(RoleName.ADMIN, RoleName.FACULTY)).roles());
        verify(userSessions).endAll(57L, SessionEndReason.ENDED_BY_COLLEGE);
        assertTrue(account.hasRole(RoleName.ADMIN));
        verify(audit).record(ADMIN, com.stacc.backend.audit.AuditAction.USER_ROLES_CHANGED, "USER_ACCOUNT", 57L,
                "FACULTY to FACULTY, ADMIN.");

        assertRefused(HttpStatus.CONFLICT, UserAdminService.SELF_ADMIN,
                () -> service.updateRoles(ADMIN, ADMIN, Set.of(RoleName.FACULTY)));
    }

    @Test
    void aTemporaryPasswordMustFollowThePolicyAndNeverIncludeTheLoginId() {
        stored(57L, "25CSE104", RoleName.STUDENT);

        assertRefused(HttpStatus.BAD_REQUEST, null, () -> service.issueTemporaryPassword(ADMIN, 57L, "my25CSE104pass"));
        verify(passwords, never()).issueTemporaryPassword(anyLong(), anyString(), any());

        service.issueTemporaryPassword(ADMIN, 57L, TEMPORARY);
        verify(passwords).issueTemporaryPassword(57L, TEMPORARY, null);
    }

    @Test
    void profilesNeedTheMatchingRoleAndAreCreatedOnce() {
        stored(57L, "EMP204", RoleName.FACULTY);

        assertRefused(HttpStatus.CONFLICT, UserAdminService.NOT_A_STUDENT,
                () -> service.addStudentProfile(ADMIN, 57L, new StudentProfileCreate(4L, 2L, null)));
        service.addFacultyProfile(ADMIN, 57L, new FacultyProfileCreate(3L));
        verify(faculty).saveAndFlush(any(FacultyProfile.class));

        when(faculty.findByUserAccountId(57L)).thenReturn(Optional.of(mock(FacultyProfile.class)));
        assertRefused(HttpStatus.CONFLICT, UserAdminService.FACULTY_EXISTS,
                () -> service.addFacultyProfile(ADMIN, 57L, new FacultyProfileCreate(3L)));
    }

    @Test
    void officialDetailsAreCorrectedAndAnEmailIsNeverShared() {
        UserAccount account = stored(57L, "25CSE104", RoleName.STUDENT);
        PersonProfile person = new PersonProfile(account, "Riya M", null);
        when(people.findByUserAccountId(57L)).thenReturn(Optional.of(person));

        UserDetail saved = service.updateDetails(ADMIN, 57L, new DetailsUpdate("Riya Menon", "riya@college.example"));

        assertEquals("Riya Menon", person.getFullName());
        assertEquals(57L, saved.accountId());
        when(people.existsByEmailAndUserAccountIdNot("other@college.example", 57L)).thenReturn(true);
        assertRefused(HttpStatus.CONFLICT, UserAdminService.EMAIL_TAKEN,
                () -> service.updateDetails(ADMIN, 57L, new DetailsUpdate("Riya Menon", "other@college.example")));
    }

    @Test
    void anUnknownAccountLooksMissing() {
        assertRefused(HttpStatus.NOT_FOUND, UserAdminService.NOT_FOUND, () -> service.get(99L));
        assertRefused(HttpStatus.NOT_FOUND, UserAdminService.NOT_FOUND,
                () -> service.updateStatus(ADMIN, 99L, AccountStatus.DISABLED));
    }
}
