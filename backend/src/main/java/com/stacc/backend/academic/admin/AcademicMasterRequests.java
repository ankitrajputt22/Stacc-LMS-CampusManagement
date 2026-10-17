package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramStatus;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What an admin may send to create or edit an academic master. No request carries an ID of its own: the record being
 * edited comes from the route. Parents are named by ID and loaded on the server. Codes, and what a record belongs to,
 * are set on creation and never changed, because other records are identified by them.
 */
public final class AcademicMasterRequests {

    private AcademicMasterRequests() {
    }

    public record DepartmentCreate(
            @Schema(description = "Stored in uppercase. Unique, and never changed afterwards.", example = "CSE")
            @NotBlank(message = "Code is required")
            @Size(max = Department.MAX_CODE_LENGTH, message = "Code must be at most 20 characters")
            String code,

            @Schema(description = "Unique across the college.", example = "Computer Science and Engineering")
            @NotBlank(message = "Name is required")
            @Size(max = Department.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name) {
    }

    public record DepartmentUpdate(
            @Schema(example = "Computer Science and Engineering")
            @NotBlank(message = "Name is required")
            @Size(max = Department.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            DepartmentStatus status) {
    }

    public record ProgramCreate(
            @Schema(description = "The department that offers the program. It is loaded on the server.", example = "3")
            @NotNull(message = "Department is required")
            Long departmentId,

            @Schema(description = "Stored in uppercase. Unique within the department, and never changed afterwards.",
                    example = "BTECH-CSE")
            @NotBlank(message = "Code is required")
            @Size(max = Program.MAX_CODE_LENGTH, message = "Code must be at most 30 characters")
            String code,

            @Schema(description = "Unique within the department.", example = "Bachelor of Technology in CSE")
            @NotBlank(message = "Name is required")
            @Size(max = Program.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(description = "The normal length of the program in semesters, from 1 to 20.", example = "8")
            @NotNull(message = "Duration is required")
            @Min(value = 1, message = "Duration must be at least 1 semester")
            @Max(value = Program.MAX_DURATION_SEMESTERS, message = "Duration must be at most 20 semesters")
            Integer durationSemesters) {
    }

    public record ProgramUpdate(
            @Schema(example = "Bachelor of Technology in CSE")
            @NotBlank(message = "Name is required")
            @Size(max = Program.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(description = "From 1 to 20, and not below the highest semester the program already has.", example = "8")
            @NotNull(message = "Duration is required")
            @Min(value = 1, message = "Duration must be at least 1 semester")
            @Max(value = Program.MAX_DURATION_SEMESTERS, message = "Duration must be at most 20 semesters")
            Integer durationSemesters,

            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            ProgramStatus status) {
    }

    public record AcademicSessionCreate(
            @Schema(description = "The college's own name for the session, stored with letters in uppercase. Unique, and "
                    + "never changed afterwards. It is never worked out from the dates.", example = "2026-27")
            @NotBlank(message = "Code is required")
            @Size(max = AcademicSession.MAX_CODE_LENGTH, message = "Code must be at most 20 characters")
            String code,

            @Schema(description = "A calendar date, such as 2026-07-01.", example = "2026-07-01")
            @NotNull(message = "Start date is required")
            LocalDate startDate,

            @Schema(description = "A calendar date after the start date.", example = "2027-06-30")
            @NotNull(message = "End date is required")
            LocalDate endDate) {
    }

    public record AcademicSessionUpdate(
            @Schema(example = "2026-07-01")
            @NotNull(message = "Start date is required")
            LocalDate startDate,

            @Schema(example = "2027-06-30")
            @NotNull(message = "End date is required")
            LocalDate endDate,

            @Schema(description = "PLANNED, ACTIVE, or CLOSED. A session cannot go back to PLANNED, and a closed one cannot "
                    + "be made active again.", allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            AcademicSessionStatus status) {
    }

    public record SemesterCreate(
            @Schema(description = "The program the semester belongs to. It is loaded on the server.", example = "4")
            @NotNull(message = "Program is required")
            Long programId,

            @Schema(description = "The academic session it takes place in. It is loaded on the server.", example = "2")
            @NotNull(message = "Academic session is required")
            Long academicSessionId,

            @Schema(description = "Which semester of the program, from 1 up to the program's duration.", example = "3")
            @NotNull(message = "Semester number is required")
            @Min(value = 1, message = "Semester number must be at least 1")
            @Max(value = Program.MAX_DURATION_SEMESTERS, message = "Semester number must be at most 20")
            Integer semesterNumber) {
    }

    /** A semester's program, session, and number say what it is, so only its status can change. */
    public record SemesterUpdate(
            @Schema(description = "PLANNED, ACTIVE, or CLOSED. A semester cannot go back to PLANNED, and a closed one "
                    + "cannot be made active again.", allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            SemesterStatus status) {
    }

    public record CourseCreate(
            @Schema(description = "The department responsible for the course. It is loaded on the server.", example = "3")
            @NotNull(message = "Department is required")
            Long departmentId,

            @Schema(description = "Stored in uppercase. Unique across the college, and never changed afterwards.",
                    example = "CS301")
            @NotBlank(message = "Code is required")
            @Size(max = Course.MAX_CODE_LENGTH, message = "Code must be at most 30 characters")
            String code,

            @Schema(example = "Data Structures")
            @NotBlank(message = "Name is required")
            @Size(max = Course.MAX_NAME_LENGTH, message = "Name must be at most 200 characters")
            String name,

            @Schema(description = "Greater than zero and at most 20, with at most two decimal places.", example = "4")
            @NotNull(message = "Credits are required")
            @DecimalMin(value = "0", inclusive = false, message = "Credits must be greater than zero")
            @DecimalMax(value = "20.00", message = "Credits must be at most 20")
            @Digits(integer = 2, fraction = 2, message = "Credits can have at most 2 decimal places")
            BigDecimal credits) {
    }

    public record CourseUpdate(
            @Schema(example = "Data Structures")
            @NotBlank(message = "Name is required")
            @Size(max = Course.MAX_NAME_LENGTH, message = "Name must be at most 200 characters")
            String name,

            @Schema(example = "4")
            @NotNull(message = "Credits are required")
            @DecimalMin(value = "0", inclusive = false, message = "Credits must be greater than zero")
            @DecimalMax(value = "20.00", message = "Credits must be at most 20")
            @Digits(integer = 2, fraction = 2, message = "Credits can have at most 2 decimal places")
            BigDecimal credits,

            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            CourseStatus status) {
    }
}
