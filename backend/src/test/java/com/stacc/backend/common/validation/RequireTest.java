package com.stacc.backend.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RequireTest {

    @Test
    void presentValueIsReturnedAndMissingValueNamesTheField() {
        assertEquals("value", Require.notNull("value", "field"));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Require.notNull(null, "department"));
        assertEquals("department is required", exception.getMessage());
    }

    @Test
    void textIsTrimmedAndMustNotBeBlankOrTooLong() {
        assertEquals("CSE", Require.text("  CSE ", "code", 20));
        assertEquals("abcde", Require.text("abcde", "code", 5));

        assertThrows(IllegalArgumentException.class, () -> Require.text(null, "code", 20));
        assertThrows(IllegalArgumentException.class, () -> Require.text("   ", "code", 20));
        IllegalArgumentException tooLong =
                assertThrows(IllegalArgumentException.class, () -> Require.text("abcdef", "code", 5));
        assertEquals("code must be at most 5 characters", tooLong.getMessage());
    }

    @Test
    void numberMustBeInsideItsRange() {
        assertEquals(1, Require.range(1, "duration", 1, 20));
        assertEquals(20, Require.range(20, "duration", 1, 20));

        assertThrows(IllegalArgumentException.class, () -> Require.range(0, "duration", 1, 20));
        assertThrows(IllegalArgumentException.class, () -> Require.range(21, "duration", 1, 20));
    }
}
