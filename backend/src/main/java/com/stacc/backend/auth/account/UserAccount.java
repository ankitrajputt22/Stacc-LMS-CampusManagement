package com.stacc.backend.auth.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
