package com.stacc.backend.academic.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import com.stacc.backend.academic.department.Department;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CourseTest {

    private static final String NAME = "Data Structures";
    private static final BigDecimal FOUR = new BigDecimal("4.00");

    private final Department department = new Department("CSE", "Computer Science and Engineering");

    @Test
    void newCourseIsActiveAndBelongsToItsDepartment() {
        Course course = new Course(department, "BCS301", NAME, FOUR);

        assertSame(department, course.getDepartment());
        assertEquals("BCS301", course.getCode());
        assertEquals(NAME, course.getName());
        assertEquals(FOUR, course.getCredits());
        assertEquals(CourseStatus.ACTIVE, course.getStatus());
    }

    @Test
    void departmentIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Course(null, "BCS301", NAME, FOUR));
    }

    @Test
    void codeIsTrimmedAndStoredInUppercaseWithoutAssumingAFormat() {
        assertEquals("BCS301", new Course(department, "  bcs301 ", NAME, FOUR).getCode());
        assertEquals("CS-DS/2026", new Course(department, "cs-ds/2026", NAME, FOUR).getCode());
    }

    @Test
    void codeIsRequiredAndLimitedInLength() {
        assertThrows(IllegalArgumentException.class, () -> new Course(department, null, NAME, FOUR));
        assertThrows(IllegalArgumentException.class, () -> new Course(department, " ", NAME, FOUR));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Course(department, "C".repeat(Course.MAX_CODE_LENGTH + 1), NAME, FOUR));
    }

    @Test
    void nameIsTrimmedRequiredAndLimitedInLength() {
        assertEquals(NAME, new Course(department, "BCS301", "  " + NAME + " ", FOUR).getName());
        assertThrows(IllegalArgumentException.class, () -> new Course(department, "BCS301", null, FOUR));
        assertThrows(IllegalArgumentException.class, () -> new Course(department, "BCS301", "  ", FOUR));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Course(department, "BCS301", "N".repeat(Course.MAX_NAME_LENGTH + 1), FOUR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "4.0", "4.00", "1.50", "0.5", "0.25", "20", "20.00"})
    void wholeAndFractionalCreditsAreAcceptedAndKeptWithTwoDecimalPlaces(String credits) {
        Course course = new Course(department, "BCS301", NAME, new BigDecimal(credits));

        assertEquals(0, new BigDecimal(credits).compareTo(course.getCredits()));
        assertEquals(2, course.getCredits().scale());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.50", "20.01", "21", "100"})
    void creditsMustBeGreaterThanZeroAndWithinTheLimit(String credits) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Course(department, "BCS301", NAME, new BigDecimal(credits)));
    }

    @Test
    void creditsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Course(department, "BCS301", NAME, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.456", "1.005", "0.001"})
    void creditsWithTooManyDecimalPlacesAreRejectedNotRounded(String credits) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Course(department, "BCS301", NAME, new BigDecimal(credits)));
    }

    @Test
    void courseCanBeRenamedAndItsCreditsChangedWithinTheRules() {
        Course course = new Course(department, "BCS301", NAME, FOUR);

        course.rename(" Data Structures and Algorithms ");
        course.changeCredits(new BigDecimal("3.5"));

        assertEquals("Data Structures and Algorithms", course.getName());
        assertEquals(new BigDecimal("3.50"), course.getCredits());
        assertThrows(IllegalArgumentException.class, () -> course.rename(null));
        assertThrows(IllegalArgumentException.class, () -> course.changeCredits(BigDecimal.ZERO));
        assertEquals(new BigDecimal("3.50"), course.getCredits());
    }

    @Test
    void courseCanBeMadeInactiveAndActiveAgain() {
        Course course = new Course(department, "BCS301", NAME, FOUR);

        course.deactivate();
        assertEquals(CourseStatus.INACTIVE, course.getStatus());

        course.activate();
        assertEquals(CourseStatus.ACTIVE, course.getStatus());
    }
}
