package com.stacc.backend.auth.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PermissionTest {

    @ParameterizedTest
    @ValueSource(strings = {"COURSE_VIEW", "ATTENDANCE_MARK", "REPORT", "YEAR2_RESULT_VIEW"})
    void uppercaseCodeWithUnderscoresIsAccepted(String code) {
        assertEquals(code, new Permission(code).getCode());
    }

    @Test
    void surroundingSpacesAreRemoved() {
        assertEquals("COURSE_VIEW", new Permission("  COURSE_VIEW ").getCode());
    }

    @Test
    void codeIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Permission(null));
        assertThrows(IllegalArgumentException.class, () -> new Permission("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "course_view", "Course_View", "COURSE VIEW", "COURSE-VIEW", "COURSE.VIEW",
        "_COURSE", "COURSE_", "COURSE__VIEW", "2COURSE", "*"
    })
    void codeInAnyOtherStyleIsRejectedRatherThanChanged(String code) {
        assertThrows(IllegalArgumentException.class, () -> new Permission(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_STUDENT", "ROLE_ANYTHING"})
    void codeCannotLookLikeARole(String code) {
        assertThrows(IllegalArgumentException.class, () -> new Permission(code));
        assertEquals("ROLES_VIEW", new Permission("ROLES_VIEW").getCode());
    }

    @Test
    void codeCannotBeLongerThanTheColumn() {
        String longestAllowed = "A".repeat(Permission.MAX_CODE_LENGTH);
        String tooLong = "A".repeat(Permission.MAX_CODE_LENGTH + 1);

        assertEquals(longestAllowed, new Permission(longestAllowed).getCode());
        assertThrows(IllegalArgumentException.class, () -> new Permission(tooLong));
    }

    @Test
    void permissionsWithTheSameCodeAreTheSamePermission() {
        assertEquals(new Permission("COURSE_VIEW"), new Permission("COURSE_VIEW"));
        assertEquals(new Permission("COURSE_VIEW").hashCode(), new Permission("COURSE_VIEW").hashCode());
        assertNotEquals(new Permission("COURSE_VIEW"), new Permission("COURSE_MANAGE"));
    }
}
