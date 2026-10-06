package com.stacc.backend.auth.role;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.stacc.backend.auth.permission.Permission;
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

    // No cascade: permissions are shared reference data and must never be created or deleted through a role.
    @ManyToMany
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

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

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    /** Tells only whether this role currently holds the permission. It is not an access check. */
    public boolean hasPermission(String code) {
        return permissions.stream().anyMatch(permission -> permission.getCode().equals(code));
    }

    public void assignPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
        permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
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
