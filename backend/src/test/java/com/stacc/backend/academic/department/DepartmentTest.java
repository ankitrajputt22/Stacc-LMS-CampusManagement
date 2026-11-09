package com.stacc.backend.academic.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DepartmentTest {

    private static final String NAME = "Computer Science and Engineering";

    @Test
    void newDepartmentIsActive() {
        Department department = new Department("CSE", NAME);

        assertNull(department.getId());
        assertEquals("CSE", department.getCode());
        assertEquals(NAME, department.getName());
        assertEquals(DepartmentStatus.ACTIVE, department.getStatus());
    }

    @Test
    void codeIsTrimmedAndStoredInUppercase() {
        assertEquals("CSE", new Department("  cse ", NAME).getCode());
        assertEquals("AI-ML", new Department("Ai-Ml", NAME).getCode());
    }

    @Test
    void codeIsRequiredAndLimitedInLength() {
        assertThrows(IllegalArgumentException.class, () -> new Department(null, NAME));
        assertThrows(IllegalArgumentException.class, () -> new Department("   ", NAME));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Department("C".repeat(Department.MAX_CODE_LENGTH + 1), NAME));
        assertEquals(
                Department.MAX_CODE_LENGTH,
                new Department("C".repeat(Department.MAX_CODE_LENGTH), NAME).getCode().length());
    }

    @Test
    void nameIsTrimmedRequiredAndLimitedInLength() {
        assertEquals(NAME, new Department("CSE", "  " + NAME + " ").getName());
        assertThrows(IllegalArgumentException.class, () -> new Department("CSE", null));
        assertThrows(IllegalArgumentException.class, () -> new Department("CSE", "  "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Department("CSE", "N".repeat(Department.MAX_NAME_LENGTH + 1)));
    }

    @Test
    void departmentCanBeRenamedButNotToAnInvalidName() {
        Department department = new Department("CSE", NAME);

        department.rename("  Computer Science ");

        assertEquals("Computer Science", department.getName());
        assertThrows(IllegalArgumentException.class, () -> department.rename(" "));
        assertEquals("Computer Science", department.getName());
    }

    @Test
    void departmentCanBeMadeInactiveAndActiveAgain() {
        Department department = new Department("CSE", NAME);

        department.deactivate();
        assertEquals(DepartmentStatus.INACTIVE, department.getStatus());

        department.activate();
        assertEquals(DepartmentStatus.ACTIVE, department.getStatus());
    }
}
