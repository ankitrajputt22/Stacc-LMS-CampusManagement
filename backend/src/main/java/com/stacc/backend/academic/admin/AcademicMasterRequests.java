package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.academic.program.DegreeType;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramStatus;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionStatus;
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
            String name,
            @Schema(description = "Optional plain text about the department, up to 1000 characters.",
                    example = "Teaches computing from first year to research.")
            @Size(max = Department.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 1000 characters")
            String description) {
    }

    public record DepartmentUpdate(
            @Schema(example = "Computer Science and Engineering")
            @NotBlank(message = "Name is required")
            @Size(max = Department.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            DepartmentStatus status,
            @Schema(description = "Optional plain text about the department, up to 1000 characters.",
                    example = "Teaches computing from first year to research.")
            @Size(max = Department.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 1000 characters")
            String description) {
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
            Integer durationSemesters,
            @Schema(description = "What kind of degree the program leads to. UNDERGRADUATE when left out.",
                    allowableValues = {"UNDERGRADUATE", "POSTGRADUATE", "DIPLOMA", "DOCTORATE", "CERTIFICATE"},
                    example = "UNDERGRADUATE")
            DegreeType degreeType,
            @Schema(description = "The most credits a student of this program may register for in one semester, more "
                    + "than 0 and at most 60. Empty means no limit.", example = "24")
            @DecimalMin(value = "0", inclusive = false, message = "Credit limit must be greater than zero")
            @DecimalMax(value = "60.00", message = "Credit limit must be at most 60")
            @Digits(integer = 2, fraction = 2, message = "Credit limit can have at most 2 decimal places")
            BigDecimal maxCreditsPerSemester) {
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
            ProgramStatus status,
            @Schema(description = "What kind of degree the program leads to. Unchanged when left out.",
                    allowableValues = {"UNDERGRADUATE", "POSTGRADUATE", "DIPLOMA", "DOCTORATE", "CERTIFICATE"},
                    example = "UNDERGRADUATE")
            DegreeType degreeType,
            @Schema(description = "The most credits a student of this program may register for in one semester, more "
                    + "than 0 and at most 60. Empty means no limit.", example = "24")
            @DecimalMin(value = "0", inclusive = false, message = "Credit limit must be greater than zero")
            @DecimalMax(value = "60.00", message = "Credit limit must be at most 60")
            @Digits(integer = 2, fraction = 2, message = "Credit limit can have at most 2 decimal places")
            BigDecimal maxCreditsPerSemester) {
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

    /**
     * A semester's registration period and drop deadline, all optional. Times carry a zone or offset, such as
     * 2026-07-01T09:00:00+05:30, and are stored in UTC. Sending none clears the period.
     */
    public record SemesterRegistrationUpdate(
            @Schema(description = "When students may start registering. Needed together with the closing time.",
                    example = "2026-07-01T09:00:00+05:30")
            Instant registrationOpensAt,

            @Schema(description = "When registration closes; after the opening time.", example = "2026-07-15T17:00:00+05:30")
            Instant registrationClosesAt,

            @Schema(description = "The last moment a student may drop a registered course. Optional, never before "
                    + "registration opens. Without it students cannot drop.", example = "2026-07-31T17:00:00+05:30")
            Instant dropDeadline) {
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
            BigDecimal credits,
            @Schema(description = "An optional short form, such as DBMS, up to 20 characters.", example = "DSA")
            @Size(max = Course.MAX_SHORT_NAME_LENGTH, message = "Short name must be at most 20 characters")
            String shortName,
            @Schema(description = "Optional plain text about the course, up to 2000 characters.")
            @Size(max = Course.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description,
            @Schema(description = "The kind of course. CORE when left out.",
                    allowableValues = {"CORE", "ELECTIVE", "LAB", "PROJECT", "AUDIT"}, example = "CORE")
            CourseType courseType) {
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
            CourseStatus status,
            @Schema(description = "An optional short form, such as DBMS, up to 20 characters.", example = "DSA")
            @Size(max = Course.MAX_SHORT_NAME_LENGTH, message = "Short name must be at most 20 characters")
            String shortName,
            @Schema(description = "Optional plain text about the course, up to 2000 characters.")
            @Size(max = Course.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 2000 characters")
            String description,
            @Schema(description = "The kind of course. Unchanged when left out.",
                    allowableValues = {"CORE", "ELECTIVE", "LAB", "PROJECT", "AUDIT"}, example = "CORE")
            CourseType courseType) {
    }

    public record SectionCreate(
            @Schema(description = "The semester the section belongs to. It is loaded on the server.", example = "9")
            @NotNull(message = "Semester is required")
            Long semesterId,

            @Schema(description = "Stored in uppercase. Unique within the semester, and never changed afterwards.", example = "A")
            @NotBlank(message = "Code is required")
            @Size(max = Section.MAX_CODE_LENGTH, message = "Code must be at most 10 characters")
            String code,

            @Schema(description = "An optional longer name, up to 60 characters.", example = "Section A")
            @Size(max = Section.MAX_NAME_LENGTH, message = "Name must be at most 60 characters")
            String name,

            @Schema(description = "The most enrolled students it holds, from 1 to 1000. No limit when left out.", example = "60")
            @Min(value = 1, message = "Capacity must be at least 1")
            @Max(value = Section.MAX_CAPACITY, message = "Capacity must be at most 1000")
            Integer capacity) {
    }

    public record SectionUpdate(
            @Schema(example = "Section A")
            @Size(max = Section.MAX_NAME_LENGTH, message = "Name must be at most 60 characters")
            String name,

            @Schema(description = "From 1 to 1000, and not below the students it already holds. No limit when left out.",
                    example = "60")
            @Min(value = 1, message = "Capacity must be at least 1")
            @Max(value = Section.MAX_CAPACITY, message = "Capacity must be at most 1000")
            Integer capacity,

            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
            @NotNull(message = "Status is required")
            SectionStatus status) {
    }
}
