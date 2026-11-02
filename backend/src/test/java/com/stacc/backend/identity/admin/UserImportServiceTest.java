package com.stacc.backend.identity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import com.stacc.backend.audit.AuditAction;
import com.stacc.backend.audit.AuditService;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserImportServiceTest {

    private static final String HEADER = "login_id,full_name,email,roles,department,program,admission_session,roll_number\n";
    private static final String TEMPORARY = "Bright-Lantern-2047";

    private final UserAdminService users = mock(UserAdminService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PersonProfileRepository people = mock(PersonProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserImportService service = new UserImportService(users, accounts, people, students, departments,
            programs, sessions, encoder, audit);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @BeforeEach
    void setUp() {
        Department cse = withId(new Department("CSE", "Computer Science"), 3);
        when(departments.findByCode("CSE")).thenReturn(Optional.of(cse));
        when(programs.findByDepartmentIdAndCode(3L, "BTECH-CSE")).thenReturn(Optional.of(withId(new Program(cse, "BTECH-CSE", "B.Tech.", 8), 4)));
        when(sessions.findByCode("2026-27")).thenReturn(Optional.of(withId(new AcademicSession("2026-27",
                LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30)), 2)));
        when(encoder.encode(TEMPORARY)).thenReturn("{test}hash");
    }

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "people.csv", "text/csv", (HEADER + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validRowsAreCreatedTogetherWithOneHashAndOneSummaryRecord() {
        UserImportService.ImportResult result = service.importCsv(1L, csv(
                "26CSE001,Riya Menon,riya@college.example,STUDENT,cse,btech-cse,2026-27,26cse001\n"
                        + "EMP301,\"Das, Kiran\",,FACULTY;ADMIN,CSE,,,\n"), TEMPORARY, false);

        assertEquals(2, result.created());
        assertEquals(0, result.rowsWithErrors());
        ArgumentCaptor<UserCreate> created = ArgumentCaptor.forClass(UserCreate.class);
        verify(users, times(2)).createWithHash(eq(1L), created.capture(), eq("{test}hash"));
        verify(encoder, times(1)).encode(anyString());
        assertEquals("26CSE001", created.getAllValues().get(0).studentProfile().rollNumber());
        assertEquals("Das, Kiran", created.getAllValues().get(1).fullName());
        assertEquals(Set.of(RoleName.FACULTY, RoleName.ADMIN), created.getAllValues().get(1).roles());
        verify(audit).record(1L, AuditAction.USERS_IMPORTED, "USER_IMPORT", null, "Imported 2 accounts from a CSV file.");
    }

    @Test
    void aCheckSavesNothingAndEveryProblemIsListedByLine() {
        when(accounts.existsByLoginId("student1")).thenReturn(true);

        UserImportService.ImportResult result = service.importCsv(1L, csv(
                "student1,Taken,,STUDENT,CSE,BTECH-CSE,2026-27,\n"
                        + "NEW1,,bad-email,TEACHER,,,,\n"
                        + "NEW2,Fine,,STUDENT,CSE,MCA,1999-00,\n"
                        + "new2,Again,,ADMIN,,,,\n"
                        + "NEW3,Fine Person,,ADMIN,,,,\n"), TEMPORARY, true);

        assertEquals(0, result.created());
        assertEquals(4, result.rowsWithErrors());
        assertEquals(List.of("An account with this login ID already exists."), result.rows().get(0).errors());
        assertTrue(result.rows().get(1).errors().contains("The full name is missing."));
        assertTrue(result.rows().get(1).errors().contains("The email is not valid."));
        assertTrue(result.rows().get(1).errors().stream().anyMatch(error -> error.startsWith("Unknown role TEACHER")));
        assertTrue(result.rows().get(2).errors().contains("Department CSE has no program MCA."));
        assertTrue(result.rows().get(2).errors().contains("No academic session has the code 1999-00."));
        assertEquals(List.of("The login ID repeats line 4."), result.rows().get(3).errors());
        assertEquals(List.of(), result.rows().get(4).errors());
        verify(users, never()).createWithHash(any(), any(), any());
    }

    @Test
    void oneBadRowStopsTheWholeImport() {
        UserImportService.ImportResult result = service.importCsv(1L, csv(
                "NEW1,Fine Person,,ADMIN,,,,\nNEW2,,,ADMIN,,,,\n"), TEMPORARY, false);

        assertEquals(0, result.created());
        verify(users, never()).createWithHash(any(), any(), any());
    }

    @Test
    void anUnusableFileOrPasswordIsRefusedOutright() {
        for (MockMultipartFile bad : new MockMultipartFile[] {
                new MockMultipartFile("file", "a.csv", "text/csv", "wrong,header\nx,y\n".getBytes()),
                new MockMultipartFile("file", "a.csv", "text/csv", HEADER.getBytes()),
                new MockMultipartFile("file", "a.csv", "text/csv", new byte[] {(byte) 0xC3, (byte) 0x28}),
                new MockMultipartFile("file", "a.csv", "text/csv", new byte[0])}) {
            ApiException refused = assertThrows(ApiException.class, () -> service.importCsv(1L, bad, TEMPORARY, true));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        }
        assertThrows(ApiException.class, () -> service.importCsv(1L, csv("NEW1,A,,ADMIN,,,,\n"), "short", true));
    }

    @Test
    void theTemporaryPasswordIsCheckedAgainstEachLoginId() {
        UserImportService.ImportResult result = service.importCsv(1L, csv("lantern,A Person,,ADMIN,,,,\n"),
                "Bright-Lantern-2047", true);

        assertTrue(result.rows().get(0).errors().get(0).startsWith("The temporary password does not suit this login ID"));
    }
}
