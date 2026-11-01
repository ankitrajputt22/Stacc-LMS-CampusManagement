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

/**
 * Passwords after sign-in: a person changing their own, and the college issuing a temporary one. Passwords are hashed
 * and checked only with the shared {@link PasswordEncoder}; no password or hash is ever logged or returned.
 */
@Service
public class PasswordService {

    static final String WRONG_CURRENT = "Your current password is not correct.";
    static final String SAME_AS_CURRENT = "Choose a password different from your current one.";
    /** How long a temporary password works when the college does not say. */
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

    /**
     * Changes the signed-in person's password after checking the current one. Every other session of the account ends,
     * so a device someone else may still have open is signed out; this one stays signed in.
     */
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

    /**
     * Gives an account a temporary password that works until it expires and only for choosing a new one, and ends its
     * sessions. For the college's account tools; the temporary password is shown once to the person issuing it.
     */
    @Transactional
    public void issueTemporaryPassword(Long accountId, String temporaryPassword, Duration validity) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
        Instant expiresAt = clock.instant().plus(validity == null ? TEMPORARY_VALIDITY : validity);
        account.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
        accounts.save(account);
        sessions.endAll(accountId, SessionEndReason.ENDED_BY_COLLEGE);
    }

    /** Whether the account must choose a new password before anything else. */
    @Transactional(readOnly = true)
    public boolean isChangeRequired(Long accountId) {
        return accounts.findById(accountId).map(UserAccount::isPasswordChangeRequired).orElse(false);
    }
}
