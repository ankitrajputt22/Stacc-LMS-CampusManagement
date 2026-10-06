package com.stacc.backend.academic.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stacc.backend.academic.department.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProgramTest {

    private static final String NAME = "Bachelor of Technology";

    private final Department department = new Department("CSE", "Computer Science and Engineering");

    @Test
    void newProgramIsActiveAndBelongsToItsDepartment() {
        Program program = new Program(department, "BTECH", NAME, 8);

        assertSame(department, program.getDepartment());
        assertEquals("BTECH", program.getCode());
        assertEquals(NAME, program.getName());
        assertEquals(8, program.getDurationSemesters());
        assertEquals(ProgramStatus.ACTIVE, program.getStatus());
    }

    @Test
    void departmentIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Program(null, "BTECH", NAME, 8));
    }

    @Test
    void codeIsTrimmedAndStoredInUppercase() {
        assertEquals("BTECH", new Program(department, "  btech ", NAME, 8).getCode());
    }

    @Test
    void codeIsRequiredAndLimitedInLength() {
        assertThrows(IllegalArgumentException.class, () -> new Program(department, null, NAME, 8));
        assertThrows(IllegalArgumentException.class, () -> new Program(department, "  ", NAME, 8));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Program(department, "B".repeat(Program.MAX_CODE_LENGTH + 1), NAME, 8));
    }

    @Test
    void nameIsTrimmedRequiredAndLimitedInLength() {
        assertEquals(NAME, new Program(department, "BTECH", " " + NAME + "  ", 8).getName());
        assertThrows(IllegalArgumentException.class, () -> new Program(department, "BTECH", null, 8));
        assertThrows(IllegalArgumentException.class, () -> new Program(department, "BTECH", " ", 8));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Program(department, "BTECH", "N".repeat(Program.MAX_NAME_LENGTH + 1), 8));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -8, 21, 100})
    void durationMustBeAPositiveNumberOfSemestersWithinTheLimit(int duration) {
        assertThrows(IllegalArgumentException.class, () -> new Program(department, "BTECH", NAME, duration));
    }

    @Test
    void shortestAndLongestDurationsAreAccepted() {
        assertEquals(1, new Program(department, "CERT", "Certificate", 1).getDurationSemesters());
        assertEquals(
                Program.MAX_DURATION_SEMESTERS,
                new Program(department, "LONG", "Long Programme", Program.MAX_DURATION_SEMESTERS)
                        .getDurationSemesters());
    }

    @Test
    void sameCodeCanBeUsedByProgramsOfDifferentDepartments() {
        Department electronics = new Department("ECE", "Electronics and Communication Engineering");

        Program first = new Program(department, "BTECH", NAME, 8);
        Program second = new Program(electronics, "BTECH", NAME, 8);

        assertEquals(first.getCode(), second.getCode());
        assertSame(electronics, second.getDepartment());
    }

    @Test
    void programCanBeRenamedAndItsDurationChangedWithinTheRules() {
        Program program = new Program(department, "BTECH", NAME, 8);

        program.rename("  B.Tech ");
        program.changeDuration(10);

        assertEquals("B.Tech", program.getName());
        assertEquals(10, program.getDurationSemesters());
        assertThrows(IllegalArgumentException.class, () -> program.rename(""));
        assertThrows(IllegalArgumentException.class, () -> program.changeDuration(0));
        assertEquals(10, program.getDurationSemesters());
    }

    @Test
    void programCanBeMadeInactiveAndActiveAgain() {
        Program program = new Program(department, "BTECH", NAME, 8);

        program.deactivate();
        assertEquals(ProgramStatus.INACTIVE, program.getStatus());

        program.activate();
        assertEquals(ProgramStatus.ACTIVE, program.getStatus());
    }
}
