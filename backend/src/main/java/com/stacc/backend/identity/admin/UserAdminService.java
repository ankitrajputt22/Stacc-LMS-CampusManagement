package com.stacc.backend.identity.admin;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.program.ProgramStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.password.PasswordPolicy;
import com.stacc.backend.auth.password.PasswordService;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.admin.UserAdminRecords.DetailsUpdate;
import com.stacc.backend.identity.admin.UserAdminRecords.FacultyProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.FacultyProfileSummary;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileSummary;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserDetail;
import com.stacc.backend.identity.admin.UserAdminRecords.UserPage;
import com.stacc.backend.identity.admin.UserAdminRecords.UserSummary;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.person.PersonProfileValues;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins find, create, and manage accounts. Every account is created by the college with a temporary password the
 * person must replace at first sign-in; there is no signup. Accounts are disabled, never deleted. Changing the status
 * or the roles ends the account's sessions, so it takes effect at once. An admin never disables their own account or
 * removes their own admin role, so the college cannot lock itself out. Passwords and hashes are never returned. Every
 * change is recorded in the audit log in the same transaction, without names, contact details, or passwords.
 */
@Service
@Transactional
public class UserAdminService {

    public static final int MAX_PAGE_SIZE = 100;

    static final String NOT_FOUND = "Account not found.";
    static final String LOGIN_TAKEN = "Another account already has this login ID.";
    static final String EMAIL_TAKEN = "Another person already has this email.";
    static final String ROLL_TAKEN = "Another student already has this roll number.";
    static final String STUDENT_PROFILE_NEEDED = "A student account needs its program and admission session.";
    static final String FACULTY_PROFILE_NEEDED = "A faculty account needs its primary department.";
    static final String NOT_A_STUDENT = "Give the account the STUDENT role before creating its student profile.";
    static final String NOT_FACULTY = "Give the account the FACULTY role before creating its faculty profile.";
    static final String STUDENT_EXISTS = "This account already has a student profile.";
    static final String FACULTY_EXISTS = "This account already has a faculty profile.";
    static final String PROGRAM_MISSING = "The selected program does not exist.";
    static final String PROGRAM_INACTIVE = "This program is inactive, so it takes no new students.";
    static final String SESSION_MISSING = "The selected academic session does not exist.";
    static final String DEPARTMENT_MISSING = "The selected department does not exist.";
    static final String DEPARTMENT_INACTIVE = "This department is inactive.";
    static final String SELF_DISABLE = "You cannot disable your own account.";
    static final String SELF_ADMIN = "You cannot remove your own ADMIN role.";
    static final String QUERY_TOO_LONG = "Search text must be at most 100 characters.";
    static final String BAD_PAGE = "Page must be 0 or more and size from 1 to 100.";

    private static final Comparator<RoleName> ROLE_ORDER = Comparator.comparingInt(RoleName::ordinal);

    private final UserAccountRepository accounts;
    private final AccountSearchRepository search;
    private final RoleRepository roles;
    private final PersonProfileRepository people;
    private final StudentProfileRepository students;
    private final FacultyProfileRepository faculty;
    private final ProgramRepository programs;
    private final AcademicSessionRepository sessions;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwords;
    private final SessionService userSessions;
    private final AuditService audit;

    public UserAdminService(UserAccountRepository accounts, AccountSearchRepository search, RoleRepository roles,
            PersonProfileRepository people, StudentProfileRepository students, FacultyProfileRepository faculty,
            ProgramRepository programs, AcademicSessionRepository sessions, DepartmentRepository departments,
            PasswordEncoder passwordEncoder, PasswordService passwords, SessionService userSessions, AuditService audit) {
        this.accounts = accounts;
        this.search = search;
        this.roles = roles;
        this.people = people;
        this.students = students;
        this.faculty = faculty;
        this.programs = programs;
        this.sessions = sessions;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.passwords = passwords;
        this.userSessions = userSessions;
        this.audit = audit;
    }

    /** One page of accounts matching the filters, by login ID. */
    @Transactional(readOnly = true)
    public UserPage list(RoleName role, AccountStatus status, String query, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(BAD_PAGE);
        }
        String text = query == null ? "" : query.strip();
        if (text.length() > 100) {
            throw badRequest(QUERY_TOO_LONG);
        }
        String pattern = text.isEmpty() ? null : "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        Page<UserAccount> found = search.search(role, status, pattern, PageRequest.of(page, size));
        Map<Long, PersonProfile> persons = personsOf(found.getContent().stream().map(UserAccount::getId).toList());
        List<UserSummary> items = found.getContent().stream().map(account -> {
            PersonProfile person = persons.get(account.getId());
            return new UserSummary(account.getId(), account.getLoginId(), person == null ? null : person.getFullName(),
                    person == null ? null : person.getEmail(), roleNames(account), account.getStatus().name(),
                    account.isPasswordChangeRequired());
        }).toList();
        return new UserPage(items, found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserDetail get(Long accountId) {
        return detailOf(find(accountId));
    }

    /**
     * Creates the account, its person profile, and the student or faculty profile its roles need, all at once or not
     * at all. The temporary password follows the password policy and must be replaced at first sign-in.
     */
    public UserDetail create(Long actorAccountId, UserCreate request) {
        checkPolicy(request.temporaryPassword(), request.loginId().strip());
        return createWithHash(actorAccountId, request, passwordEncoder.encode(request.temporaryPassword()));
    }

    /**
     * Creates one account with an already-made hash of its temporary password, whose policy the caller has checked for
     * this login ID. Bulk import hashes the shared temporary password once and calls this for every row.
     */
    public UserDetail createWithHash(Long actorAccountId, UserCreate request, String temporaryPasswordHash) {
        String loginId = request.loginId().strip();
        if (accounts.existsByLoginId(loginId)) {
            throw conflict(LOGIN_TAKEN);
        }
        String email = cleanEmail(request.email());
        if (email != null && people.existsByEmail(email)) {
            throw conflict(EMAIL_TAKEN);
        }
        Set<RoleName> wanted = EnumSet.copyOf(request.roles());
        if (wanted.contains(RoleName.STUDENT) && request.studentProfile() == null) {
            throw badRequest(STUDENT_PROFILE_NEEDED);
        }
        if (wanted.contains(RoleName.FACULTY) && request.facultyProfile() == null) {
            throw badRequest(FACULTY_PROFILE_NEEDED);
        }

        UserAccount account;
        try {
            account = new UserAccount(loginId, temporaryPasswordHash);
            for (RoleName name : wanted) {
                account.assignRole(role(name));
            }
            account = accounts.saveAndFlush(account);
            people.saveAndFlush(new PersonProfile(account, request.fullName(), email));
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        } catch (DataIntegrityViolationException race) {
            throw conflict(LOGIN_TAKEN);
        }
        audit.record(actorAccountId, AuditAction.USER_CREATED, AuditService.USER_ACCOUNT, account.getId(),
                "Created " + loginId + " with roles " + rolesText(wanted) + ".");
        if (wanted.contains(RoleName.STUDENT)) {
            createStudentProfile(actorAccountId, account, request.studentProfile());
        }
        if (wanted.contains(RoleName.FACULTY)) {
            createFacultyProfile(actorAccountId, account, request.facultyProfile());
        }
        passwords.issueTemporaryPasswordHash(account.getId(), temporaryPasswordHash, null);
        return detailOf(account);
    }

    /** Corrects the official full name and email, recording a person profile when the account has none. */
    public UserDetail updateDetails(Long actorAccountId, Long accountId, DetailsUpdate request) {
        UserAccount account = find(accountId);
        String email = cleanEmail(request.email());
        if (email != null && people.existsByEmailAndUserAccountIdNot(email, accountId)) {
            throw conflict(EMAIL_TAKEN);
        }
        try {
            PersonProfile person = people.findByUserAccountId(accountId).orElse(null);
            if (person == null) {
                people.saveAndFlush(new PersonProfile(account, request.fullName(), email));
            } else {
                person.changeOfficialDetails(request.fullName(), email);
                people.saveAndFlush(person);
            }
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        } catch (DataIntegrityViolationException race) {
            throw conflict(EMAIL_TAKEN);
        }
        audit.record(actorAccountId, AuditAction.USER_DETAILS_CHANGED, AuditService.USER_ACCOUNT, accountId,
                "Official name or email corrected.");
        return detailOf(account);
    }

    /** Activates or disables the account. Disabling ends its sessions; the same status changes nothing. */
    public UserDetail updateStatus(Long actorAccountId, Long accountId, AccountStatus status) {
        UserAccount account = accounts.findByIdForUpdate(accountId).orElseThrow(() -> notFound());
        if (account.getStatus() == status) {
            return detailOf(account);
        }
        if (status == AccountStatus.DISABLED && Objects.equals(actorAccountId, accountId)) {
            throw conflict(SELF_DISABLE);
        }
        AccountStatus before = account.getStatus();
        account.setStatus(status);
        accounts.saveAndFlush(account);
        audit.record(actorAccountId, AuditAction.USER_STATUS_CHANGED, AuditService.USER_ACCOUNT, accountId,
                before.name() + " to " + status.name() + ".");
        if (status == AccountStatus.DISABLED) {
            userSessions.endAll(accountId, SessionEndReason.ENDED_BY_COLLEGE);
        }
        return detailOf(account);
    }

    /**
     * Sets the account's roles to exactly these. A real change ends its sessions, so the new roles apply at the next
     * sign-in at once. Profiles are kept when a role is removed: they are academic history, and a profile alone grants
     * nothing.
     */
    public UserDetail updateRoles(Long actorAccountId, Long accountId, Set<RoleName> wanted) {
        UserAccount account = accounts.findByIdForUpdate(accountId).orElseThrow(() -> notFound());
        Set<RoleName> current = account.getRoles().stream().map(Role::getName)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleName.class)));
        Set<RoleName> target = EnumSet.copyOf(wanted);
        if (current.equals(target)) {
            return detailOf(account);
        }
        if (Objects.equals(actorAccountId, accountId) && current.contains(RoleName.ADMIN)
                && !target.contains(RoleName.ADMIN)) {
            throw conflict(SELF_ADMIN);
        }
        for (Role role : List.copyOf(account.getRoles())) {
            if (!target.contains(role.getName())) {
                account.removeRole(role);
            }
        }
        for (RoleName name : target) {
            if (!current.contains(name)) {
                account.assignRole(role(name));
            }
        }
        accounts.saveAndFlush(account);
        audit.record(actorAccountId, AuditAction.USER_ROLES_CHANGED, AuditService.USER_ACCOUNT, accountId,
                rolesText(current) + " to " + rolesText(target) + ".");
        userSessions.endAll(accountId, SessionEndReason.ENDED_BY_COLLEGE);
        return detailOf(account);
    }

    /** Issues a new temporary password; the person must replace it, and every session of the account ends. */
    public UserDetail issueTemporaryPassword(Long actorAccountId, Long accountId, String temporaryPassword) {
        UserAccount account = find(accountId);
        checkPolicy(temporaryPassword, account.getLoginId());
        passwords.issueTemporaryPassword(accountId, temporaryPassword, null);
        audit.record(actorAccountId, AuditAction.USER_TEMPORARY_PASSWORD_ISSUED, AuditService.USER_ACCOUNT, accountId,
                "Temporary password issued; every session ended.");
        return detailOf(find(accountId));
    }

    public UserDetail addStudentProfile(Long actorAccountId, Long accountId, StudentProfileCreate request) {
        UserAccount account = find(accountId);
        if (!account.hasRole(RoleName.STUDENT)) {
            throw conflict(NOT_A_STUDENT);
        }
        createStudentProfile(actorAccountId, account, request);
        return detailOf(account);
    }

    public UserDetail addFacultyProfile(Long actorAccountId, Long accountId, FacultyProfileCreate request) {
        UserAccount account = find(accountId);
        if (!account.hasRole(RoleName.FACULTY)) {
            throw conflict(NOT_FACULTY);
        }
        createFacultyProfile(actorAccountId, account, request);
        return detailOf(account);
    }

    private void createStudentProfile(Long actorAccountId, UserAccount account, StudentProfileCreate request) {
        if (students.findByUserAccountId(account.getId()).isPresent()) {
            throw conflict(STUDENT_EXISTS);
        }
        Program program = programs.findWithDepartmentById(request.programId())
                .orElseThrow(() -> badRequest(PROGRAM_MISSING));
        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw conflict(PROGRAM_INACTIVE);
        }
        AcademicSession admission = sessions.findById(request.admissionSessionId())
                .orElseThrow(() -> badRequest(SESSION_MISSING));
        StudentProfile profile = new StudentProfile(account, program, admission);
        String roll = request.rollNumber() == null || request.rollNumber().isBlank()
                ? null : request.rollNumber().strip().toUpperCase(Locale.ROOT);
        if (roll != null) {
            if (students.existsByRollNumber(roll)) {
                throw conflict(ROLL_TAKEN);
            }
            profile.assignRollNumber(roll);
        }
        try {
            students.saveAndFlush(profile);
        } catch (DataIntegrityViolationException race) {
            throw conflict(roll == null ? STUDENT_EXISTS : ROLL_TAKEN);
        }
        audit.record(actorAccountId, AuditAction.STUDENT_PROFILE_CREATED, AuditService.USER_ACCOUNT, account.getId(),
                "Program " + program.getCode() + ", admitted " + admission.getCode() + ".");
    }

    private void createFacultyProfile(Long actorAccountId, UserAccount account, FacultyProfileCreate request) {
        if (faculty.findByUserAccountId(account.getId()).isPresent()) {
            throw conflict(FACULTY_EXISTS);
        }
        Department department = departments.findById(request.departmentId())
                .orElseThrow(() -> badRequest(DEPARTMENT_MISSING));
        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            throw conflict(DEPARTMENT_INACTIVE);
        }
        try {
            faculty.saveAndFlush(new FacultyProfile(account, department));
        } catch (DataIntegrityViolationException race) {
            throw conflict(FACULTY_EXISTS);
        }
        audit.record(actorAccountId, AuditAction.FACULTY_PROFILE_CREATED, AuditService.USER_ACCOUNT, account.getId(),
                "Department " + department.getCode() + ".");
    }

    private UserDetail detailOf(UserAccount account) {
        PersonProfile person = people.findByUserAccountId(account.getId()).orElse(null);
        StudentProfileSummary student = students.findWithContextByUserAccountId(account.getId())
                .map(profile -> new StudentProfileSummary(profile.getId(), profile.getProgram().getCode(),
                        profile.getProgram().getName(), profile.getAdmissionSession().getCode(), profile.getRollNumber(),
                        profile.getStatus().name()))
                .orElse(null);
        FacultyProfileSummary teacher = faculty.findWithContextByUserAccountId(account.getId())
                .map(profile -> new FacultyProfileSummary(profile.getId(), profile.getDepartment().getCode(),
                        profile.getDepartment().getName(), profile.getStatus().name()))
                .orElse(null);
        return new UserDetail(account.getId(), account.getLoginId(), person == null ? null : person.getFullName(),
                person == null ? null : person.getPreferredName(), person == null ? null : person.getEmail(),
                roleNames(account), account.getStatus().name(), account.isPasswordChangeRequired(),
                account.getTemporaryPasswordExpiresAt(), account.getCreatedAt(), student, teacher,
                person != null && person.hasPhoto());
    }

    private Map<Long, PersonProfile> personsOf(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return people.findAllByUserAccountIdIn(accountIds).stream()
                .collect(Collectors.toMap(person -> person.getUserAccount().getId(), Function.identity(), (a, b) -> a));
    }

    private static String rolesText(Set<RoleName> names) {
        return names.isEmpty() ? "none" : names.stream().sorted(ROLE_ORDER).map(RoleName::name).collect(Collectors.joining(", "));
    }

    private static List<String> roleNames(UserAccount account) {
        return account.getRoles().stream().map(Role::getName).sorted(ROLE_ORDER).map(RoleName::name).toList();
    }

    private Role role(RoleName name) {
        return roles.findByName(name).orElseThrow(() -> new IllegalStateException("Role " + name + " is missing"));
    }

    private UserAccount find(Long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> notFound());
    }

    private static void checkPolicy(String password, String loginId) {
        List<String> problems = PasswordPolicy.problems(password, loginId);
        if (!problems.isEmpty()) {
            throw badRequest("The temporary password is not allowed: " + String.join(" ", problems));
        }
    }

    private static String cleanEmail(String email) {
        try {
            return PersonProfileValues.email(email);
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        }
    }

    // The search query uses ! as its escape character.
    static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
