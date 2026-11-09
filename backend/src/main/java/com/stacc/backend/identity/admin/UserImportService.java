package com.stacc.backend.identity.admin;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.password.PasswordPolicy;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.admin.UserAdminRecords.FacultyProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.person.PersonProfileValues;
import com.stacc.backend.identity.student.StudentProfileRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Creates many student and faculty accounts from one CSV file. Every row is checked first, with its problems listed
 * by line; nothing is created unless every row is valid, and then all accounts are created in one transaction, each
 * through the normal account creation and each recorded in the audit log. All imported accounts get the one temporary
 * password the admin typed, hashed once, and must replace it at first sign-in. No password is ever read from the file.
 */
@Service
public class UserImportService {

    public static final int MAX_ROWS = 500;
    public static final long MAX_BYTES = 1024L * 1024;
    static final List<String> COLUMNS = List.of("login_id", "full_name", "email", "roles", "department", "program",
            "admission_session", "roll_number");
    static final String ROLE_LIST = java.util.Arrays.stream(RoleName.values()).map(RoleName::name)
            .collect(java.util.stream.Collectors.joining(", "));
    static final String NO_FILE = "Choose a CSV file.";
    static final String TOO_LARGE = "The file must be at most 1 MB.";
    static final String NOT_TEXT = "The file must be UTF-8 text.";
    static final String NO_ROWS = "The file has no accounts after its header line.";
    static final String TOO_MANY = "The file may hold at most 500 accounts.";
    static final String BAD_HEADER = "The first line must name the columns: " + String.join(",", COLUMNS) + ".";

    private final UserAdminService users;
    private final UserAccountRepository accounts;
    private final PersonProfileRepository people;
    private final StudentProfileRepository students;
    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserImportService(UserAdminService users, UserAccountRepository accounts, PersonProfileRepository people,
            StudentProfileRepository students, DepartmentRepository departments, ProgramRepository programs,
            AcademicSessionRepository sessions, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.accounts = accounts;
        this.people = people;
        this.students = students;
        this.departments = departments;
        this.programs = programs;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public record RowResult(
            @Schema(description = "The line in the file, the header being line 1.") int line,
            String loginId,
            @Schema(description = "Every problem of the row; empty when it can be imported.") List<String> errors) {
    }

    public record ImportResult(
            @Schema(description = "True when nothing was saved because only a check was asked for.") boolean dryRun,
            @Schema(description = "How many accounts were created; 0 for a check or when any row has a problem.")
            int created,
            @Schema(description = "How many rows have problems.") int rowsWithErrors,
            List<RowResult> rows) {
    }

    private record Row(int line, String loginId, UserCreate create, List<String> errors) {
    }

    /**
     * Checks every row, and when {@code dryRun} is false and every row is valid, creates all the accounts. A file or
     * temporary password that cannot be used at all answers 400; row problems are listed in the answer instead.
     */
    @Transactional
    public ImportResult importCsv(Long actorAccountId, MultipartFile file, String temporaryPassword, boolean dryRun) {
        List<List<String>> lines = read(file);
        if (lines.isEmpty() || !header(lines.get(0))) {
            throw badRequest(BAD_HEADER);
        }
        if (lines.size() == 1) {
            throw badRequest(NO_ROWS);
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw badRequest(TOO_MANY);
        }
        if (temporaryPassword == null || temporaryPassword.length() < PasswordPolicy.MIN_LENGTH
                || temporaryPassword.length() > PasswordPolicy.MAX_LENGTH) {
            throw badRequest("The temporary password must be 10 to 128 characters.");
        }

        List<Row> rows = new ArrayList<>();
        Map<String, Integer> loginIds = new HashMap<>();
        Map<String, Integer> emails = new HashMap<>();
        Map<String, Integer> rolls = new HashMap<>();
        Lookups lookups = new Lookups();
        for (int i = 1; i < lines.size(); i++) {
            rows.add(check(i + 1, lines.get(i), temporaryPassword, loginIds, emails, rolls, lookups));
        }
        List<RowResult> results = rows.stream().map(row -> new RowResult(row.line(), row.loginId(), row.errors())).toList();
        int withErrors = (int) rows.stream().filter(row -> !row.errors().isEmpty()).count();
        if (dryRun || withErrors > 0) {
            return new ImportResult(dryRun, 0, withErrors, results);
        }

        String hash = passwordEncoder.encode(temporaryPassword);
        for (Row row : rows) {
            users.createWithHash(actorAccountId, row.create(), hash);
        }
        audit.record(actorAccountId, AuditAction.USERS_IMPORTED, "USER_IMPORT", null,
                "Imported " + rows.size() + (rows.size() == 1 ? " account" : " accounts") + " from a CSV file.");
        return new ImportResult(false, rows.size(), 0, results);
    }

    private Row check(int line, List<String> fields, String temporaryPassword, Map<String, Integer> loginIds,
            Map<String, Integer> emails, Map<String, Integer> rolls, Lookups lookups) {
        List<String> errors = new ArrayList<>();
        if (fields.size() != COLUMNS.size()) {
            errors.add("Expected " + COLUMNS.size() + " columns, found " + fields.size() + ".");
            return new Row(line, fields.isEmpty() ? "" : fields.get(0).strip(), null, errors);
        }
        String loginId = fields.get(0).strip();
        String fullName = fields.get(1).strip();
        String emailText = fields.get(2).strip();
        String rolesText = fields.get(3).strip();
        String departmentCode = fields.get(4).strip().toUpperCase(Locale.ROOT);
        String programCode = fields.get(5).strip().toUpperCase(Locale.ROOT);
        String sessionCode = fields.get(6).strip().toUpperCase(Locale.ROOT);
        String roll = fields.get(7).strip().toUpperCase(Locale.ROOT);

        if (loginId.isEmpty()) {
            errors.add("The login ID is missing.");
        } else if (loginId.length() > UserAccount.MAX_LOGIN_ID_LENGTH) {
            errors.add("The login ID is longer than 100 characters.");
        } else {
            Integer earlier = loginIds.putIfAbsent(loginId.toLowerCase(Locale.ROOT), line);
            if (earlier != null) {
                errors.add("The login ID repeats line " + earlier + ".");
            } else if (accounts.existsByLoginId(loginId)) {
                errors.add("An account with this login ID already exists.");
            }
            errors.addAll(PasswordPolicy.problems(temporaryPassword, loginId).stream()
                    .map(problem -> "The temporary password does not suit this login ID: " + problem).toList());
        }
        if (fullName.isEmpty()) {
            errors.add("The full name is missing.");
        } else if (fullName.length() > PersonProfileValues.MAX_FULL_NAME) {
            errors.add("The full name is longer than 150 characters.");
        }
        String email = null;
        if (!emailText.isEmpty()) {
            try {
                email = PersonProfileValues.email(emailText);
                Integer earlier = emails.putIfAbsent(email, line);
                if (earlier != null) {
                    errors.add("The email repeats line " + earlier + ".");
                } else if (people.existsByEmail(email)) {
                    errors.add("Another person already has this email.");
                }
            } catch (IllegalArgumentException invalid) {
                errors.add("The email is not valid.");
            }
        }

        Set<RoleName> roles = EnumSet.noneOf(RoleName.class);
        for (String part : rolesText.split("[;|]")) {
            String name = part.strip().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            try {
                roles.add(RoleName.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                errors.add("Unknown role " + name + "; use " + ROLE_LIST + ", separated by ;.");
            }
        }
        if (roles.isEmpty() && errors.stream().noneMatch(error -> error.startsWith("Unknown role"))) {
            errors.add("At least one role is needed.");
        }

        Department department = null;
        if (roles.contains(RoleName.STUDENT) || roles.contains(RoleName.FACULTY)) {
            if (departmentCode.isEmpty()) {
                errors.add("The department is needed for a student or faculty account.");
            } else {
                department = lookups.department(departmentCode).orElse(null);
                if (department == null) {
                    errors.add("No department has the code " + departmentCode + ".");
                } else if (department.getStatus() != DepartmentStatus.ACTIVE && roles.contains(RoleName.FACULTY)) {
                    errors.add("Department " + departmentCode + " is inactive.");
                }
            }
        }
        StudentProfileCreate student = null;
        if (roles.contains(RoleName.STUDENT)) {
            Program program = null;
            AcademicSession admission = null;
            if (programCode.isEmpty()) {
                errors.add("The program is needed for a student account.");
            } else if (department != null) {
                program = lookups.program(department, programCode).orElse(null);
                if (program == null) {
                    errors.add("Department " + departmentCode + " has no program " + programCode + ".");
                } else if (program.getStatus() != ProgramStatus.ACTIVE) {
                    errors.add("Program " + programCode + " is inactive.");
                }
            }
            if (sessionCode.isEmpty()) {
                errors.add("The admission session is needed for a student account.");
            } else {
                admission = lookups.session(sessionCode).orElse(null);
                if (admission == null) {
                    errors.add("No academic session has the code " + sessionCode + ".");
                }
            }
            if (!roll.isEmpty()) {
                Integer earlier = rolls.putIfAbsent(roll, line);
                if (roll.length() > 30) {
                    errors.add("The roll number is longer than 30 characters.");
                } else if (earlier != null) {
                    errors.add("The roll number repeats line " + earlier + ".");
                } else if (students.existsByRollNumber(roll)) {
                    errors.add("Another student already has this roll number.");
                }
            }
            if (program != null && admission != null) {
                student = new StudentProfileCreate(program.getId(), admission.getId(), roll.isEmpty() ? null : roll);
            }
        }
        FacultyProfileCreate teacher = roles.contains(RoleName.FACULTY) && department != null
                ? new FacultyProfileCreate(department.getId()) : null;
        UserCreate create = errors.isEmpty()
                ? new UserCreate(loginId, fullName, email, roles, temporaryPassword, student, teacher)
                : null;
        return new Row(line, loginId, create, errors);
    }

    /** Remembers the codes looked up in this file, so each is read from the database once. */
    private final class Lookups {
        private final Map<String, Optional<Department>> departmentByCode = new HashMap<>();
        private final Map<String, Optional<Program>> programByKey = new HashMap<>();
        private final Map<String, Optional<AcademicSession>> sessionByCode = new HashMap<>();

        Optional<Department> department(String code) {
            return departmentByCode.computeIfAbsent(code, departments::findByCode);
        }

        Optional<Program> program(Department department, String code) {
            return programByKey.computeIfAbsent(department.getId() + "|" + code,
                    key -> programs.findByDepartmentIdAndCode(department.getId(), code));
        }

        Optional<AcademicSession> session(String code) {
            return sessionByCode.computeIfAbsent(code, sessions::findByCode);
        }
    }

    private static boolean header(List<String> fields) {
        if (fields.size() != COLUMNS.size()) {
            return false;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (!COLUMNS.get(i).equals(fields.get(i).strip().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static List<List<String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest(NO_FILE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw badRequest(TOO_LARGE);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (IOException unreadable) {
            throw badRequest(NOT_TEXT);
        }
        try {
            return CsvRows.parse(text);
        } catch (IllegalArgumentException broken) {
            throw badRequest(broken.getMessage());
        }
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
