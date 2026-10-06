package com.stacc.backend.academic.department;

import java.time.Instant;
import java.util.Locale;

import com.stacc.backend.common.validation.Require;
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
 * An official academic department of the college. A department that is no longer
 * used is made inactive rather than deleted, so older records can still refer to it.
 */
@Entity
@Table(name = "departments")
public class Department {

    public static final int MAX_CODE_LENGTH = 20;
    public static final int MAX_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status = DepartmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Department() {
        // Required by JPA.
    }

    /** The code is stored in uppercase without surrounding spaces, so "cse" becomes "CSE". */
    public Department(String code, String name) {
        this.code = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public DepartmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String name) {
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
    }

    public void activate() {
        this.status = DepartmentStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = DepartmentStatus.INACTIVE;
    }
}
