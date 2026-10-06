package com.stacc.backend.auth.role;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import com.stacc.backend.auth.permission.Permission;
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

    @Test
    void newRoleHasNoPermissions() {
        Role role = new Role(RoleName.FACULTY);

        assertTrue(role.getPermissions().isEmpty());
        assertFalse(role.hasPermission("COURSE_VIEW"));
    }

    @Test
    void roleCanHoldMoreThanOnePermission() {
        Role role = new Role(RoleName.FACULTY);

        role.assignPermission(new Permission("COURSE_VIEW"));
        role.assignPermission(new Permission("ATTENDANCE_MARK"));

        assertEquals(
                Set.of(new Permission("COURSE_VIEW"), new Permission("ATTENDANCE_MARK")),
                role.getPermissions());
        assertTrue(role.hasPermission("COURSE_VIEW"));
        assertTrue(role.hasPermission("ATTENDANCE_MARK"));
        assertFalse(role.hasPermission("COURSE_MANAGE"));
    }

    @Test
    void assigningTheSamePermissionTwiceKeepsOneCopy() {
        Role role = new Role(RoleName.FACULTY);

        role.assignPermission(new Permission("COURSE_VIEW"));
        role.assignPermission(new Permission("COURSE_VIEW"));

        assertEquals(1, role.getPermissions().size());
    }

    @Test
    void removingOnePermissionKeepsTheOthers() {
        Role role = new Role(RoleName.FACULTY);
        role.assignPermission(new Permission("COURSE_VIEW"));
        role.assignPermission(new Permission("ATTENDANCE_MARK"));

        role.removePermission(new Permission("ATTENDANCE_MARK"));

        assertEquals(Set.of(new Permission("COURSE_VIEW")), role.getPermissions());
    }

    @Test
    void theSamePermissionCanBelongToSeveralRoles() {
        Permission courseView = new Permission("COURSE_VIEW");
        Role faculty = new Role(RoleName.FACULTY);
        Role admin = new Role(RoleName.ADMIN);
        faculty.assignPermission(courseView);
        admin.assignPermission(courseView);

        faculty.removePermission(courseView);

        assertFalse(faculty.hasPermission("COURSE_VIEW"));
        assertTrue(admin.hasPermission("COURSE_VIEW"));
    }

    @Test
    void permissionsCanOnlyBeChangedThroughTheRole() {
        Role role = new Role(RoleName.FACULTY);

        assertThrows(IllegalArgumentException.class, () -> role.assignPermission(null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> role.getPermissions().add(new Permission("COURSE_VIEW")));
    }
}
