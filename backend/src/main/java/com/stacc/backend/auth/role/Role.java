package com.stacc.backend.auth.role;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A role that the college can assign to accounts. Role rows are shared
 * reference data, so they are never created or deleted through an account.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private RoleName name;

    protected Role() {
        // Required by JPA.
    }

    public Role(RoleName name) {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    // Two roles are the same role when they have the same unique name.
    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && name == role.getName();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
