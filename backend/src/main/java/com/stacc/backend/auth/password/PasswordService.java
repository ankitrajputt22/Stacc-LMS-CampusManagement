package com.stacc.backend.auth.password;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.session.SessionEndReason;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

    static final String WRONG_CURRENT = "Your current password is not correct.";
    static final String SAME_AS_CURRENT = "Choose a password different from your current one.";
    public static final Duration TEMPORARY_VALIDITY = Duration.ofDays(3);

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final Clock clock;

    public PasswordService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, SessionService sessions,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public void changeOwnPassword(Long accountId, String currentSessionId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in again."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WRONG_CURRENT);
        }
        List<String> problems = PasswordPolicy.problems(newPassword, account.getLoginId());
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SAME_AS_CURRENT);
        }
        account.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        accounts.save(account);
        sessions.endAllExcept(accountId, currentSessionId, SessionEndReason.PASSWORD_CHANGED);
    }

    @Transactional
    public void issueTemporaryPassword(Long accountId, String temporaryPassword, Duration validity) {
        issueTemporaryPasswordHash(accountId, passwordEncoder.encode(temporaryPassword), validity);
    }

    @Transactional
    public void issueTemporaryPasswordHash(Long accountId, String temporaryPasswordHash, Duration validity) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
        Instant expiresAt = clock.instant().plus(validity == null ? TEMPORARY_VALIDITY : validity);
        account.issueTemporaryPassword(temporaryPasswordHash, expiresAt);
        accounts.save(account);
        sessions.endAll(accountId, SessionEndReason.ENDED_BY_COLLEGE);
    }

    @Transactional(readOnly = true)
    public boolean isChangeRequired(Long accountId) {
        return accounts.findById(accountId).map(UserAccount::isPasswordChangeRequired).orElse(false);
    }
}
