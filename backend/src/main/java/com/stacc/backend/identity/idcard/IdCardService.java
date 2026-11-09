package com.stacc.backend.identity.idcard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.pass.Pass;
import com.stacc.backend.pass.PassPurpose;
import com.stacc.backend.pass.PassService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The digital ID card: the holder's name, login ID, roles, and where they study or work, with a code that lasts 90
 * seconds; opening the card again makes a new code and the old one stops working. Staff with ID_CARD_VERIFY check a
 * code: a valid one shows who it belongs to, nothing more (no contact details).
 */
@Service
@Transactional
public class IdCardService {

    static final Duration CODE_LIFETIME = Duration.ofSeconds(90);

    public record IdCard(String name, String loginId, List<String> roles, String program, String department, String code,
            Instant codeValidUntil) {
    }

    public record IdCheck(boolean valid, String message, String name, String loginId, List<String> roles, String program,
            String department) {
    }

    private final UserAccountRepository accounts;
    private final PersonProfileRepository people;
    private final StudentProfileRepository students;
    private final FacultyProfileRepository faculty;
    private final PassService passes;
    private final Clock clock;

    public IdCardService(UserAccountRepository accounts, PersonProfileRepository people, StudentProfileRepository students,
            FacultyProfileRepository faculty, PassService passes, Clock clock) {
        this.accounts = accounts;
        this.people = people;
        this.students = students;
        this.faculty = faculty;
        this.passes = passes;
        this.clock = clock;
    }

    public IdCard card(Long accountId) {
        UserAccount account = accounts.findById(accountId).filter(found -> found.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Your account is not active."));
        Instant now = clock.instant();
        PassService.IssuedPass issued = passes.issue(PassPurpose.ID_CARD, accountId, accountId, now, now.plus(CODE_LIFETIME));
        IdCheck details = details(account);
        return new IdCard(details.name(), details.loginId(), details.roles(), details.program(), details.department(), issued.code(),
                issued.pass().getValidUntil());
    }

    @Transactional(readOnly = true)
    public IdCheck check(String code) {
        Optional<Pass> pass = passes.find(code).filter(found -> found.getPurpose() == PassPurpose.ID_CARD);
        if (pass.isEmpty()) {
            return new IdCheck(false, "No ID card has this code.", null, null, List.of(), null, null);
        }
        if (!pass.get().validAt(clock.instant())) {
            return new IdCheck(false, "This code has expired. Ask the person to open their ID card again.", null, null, List.of(), null, null);
        }
        Optional<UserAccount> account = accounts.findById(pass.get().getHolderAccountId()).filter(found -> found.getStatus() == AccountStatus.ACTIVE);
        if (account.isEmpty()) {
            return new IdCheck(false, "This account is no longer active.", null, null, List.of(), null, null);
        }
        IdCheck details = details(account.get());
        return new IdCheck(true, "Valid ID card.", details.name(), details.loginId(), details.roles(), details.program(), details.department());
    }

    private IdCheck details(UserAccount account) {
        String name = people.findByUserAccountId(account.getId()).map(PersonProfile::displayName).orElse(account.getLoginId());
        String program = students.findByUserAccountId(account.getId()).map(profile -> profile.getProgram().getName()).orElse(null);
        String department = faculty.findByUserAccountId(account.getId()).map(profile -> profile.getDepartment().getName()).orElse(null);
        List<String> roles = account.getRoles().stream().map(role -> role.getName().name()).sorted().toList();
        return new IdCheck(true, null, name, account.getLoginId(), roles, program, department);
    }
}
