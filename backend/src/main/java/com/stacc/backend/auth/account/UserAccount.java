package com.stacc.backend.auth.account;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A college-issued Stacc account. It holds only what is needed to sign in;
 * student and faculty profile details belong to their own models.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    public static final int MAX_LOGIN_ID_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = MAX_LOGIN_ID_LENGTH)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    // Set when the college issues a temporary password; the person must choose a new one before anything else.
    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    // A temporary password stops working at this moment, even if it was never used.
    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // No cascade: roles are shared reference data and must never be created or deleted through an account.
    @ManyToMany
    @JoinTable(
            name = "user_account_roles",
            joinColumns = @JoinColumn(name = "user_account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
        // Required by JPA.
    }

    public UserAccount(String loginId, String passwordHash) {
        this.loginId = requireText(loginId, "loginId").strip();
        if (this.loginId.length() > MAX_LOGIN_ID_LENGTH) {
            throw new IllegalArgumentException("loginId must be at most " + MAX_LOGIN_ID_LENGTH + " characters");
        }
        this.passwordHash = requireText(passwordHash, "passwordHash");
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** The person chose a new password: it replaces the old hash, and any temporary-password state ends. */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = requireText(newPasswordHash, "passwordHash");
        this.passwordChangeRequired = false;
        this.temporaryPasswordExpiresAt = null;
        this.passwordChangedAt = now;
    }

    /** The college issued a temporary password: it works until it expires, and only to choose a new one. */
    public void issueTemporaryPassword(String temporaryPasswordHash, Instant expiresAt) {
        this.passwordHash = requireText(temporaryPasswordHash, "passwordHash");
        this.passwordChangeRequired = true;
        this.temporaryPasswordExpiresAt = expiresAt;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /** Whether the password is a temporary one that has stopped working. */
    public boolean temporaryPasswordExpiredAt(Instant now) {
        return passwordChangeRequired && temporaryPasswordExpiresAt != null && !now.isBefore(temporaryPasswordExpiresAt);
    }

    /** When the current temporary password stops working, or null when there is none. */
    public Instant getTemporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(RoleName name) {
        return roles.stream().anyMatch(role -> role.getName() == name);
    }

    public void assignRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        roles.add(role);
    }

    public void removeRole(Role role) {
        roles.remove(role);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
