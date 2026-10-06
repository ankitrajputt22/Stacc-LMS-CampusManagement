package com.stacc.backend.auth.role;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void initialRolesAreStudentFacultyAndAdmin() {
        assertArrayEquals(
                new RoleName[] {RoleName.STUDENT, RoleName.FACULTY, RoleName.ADMIN},
                RoleName.values());
    }

    @Test
    void roleNameIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Role(null));
    }

    @Test
    void rolesWithTheSameNameAreTheSameRole() {
        assertEquals(new Role(RoleName.FACULTY), new Role(RoleName.FACULTY));
        assertEquals(new Role(RoleName.FACULTY).hashCode(), new Role(RoleName.FACULTY).hashCode());
        assertNotEquals(new Role(RoleName.FACULTY), new Role(RoleName.ADMIN));
    }
}
