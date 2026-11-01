package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The academic masters as admins see them: each record's own fields and, for a child, the ID, code, and name of its
 * parents for context. Nothing else is exposed, and no entity is sent as it is.
 */
public final class AcademicMasterResponses {

    private AcademicMasterResponses() {
    }

    public record DepartmentResponse(
            @Schema(example = "3") Long id,
            @Schema(example = "CSE") String code,
            @Schema(example = "Computer Science and Engineering") String name,
            @Schema(description = "Plain text, or null when none was entered.") String description,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status) {

        static DepartmentResponse of(Department department) {
            return new DepartmentResponse(department.getId(), department.getCode(), department.getName(),
                    department.getDescription(), department.getStatus().name());
        }
    }

    public record ProgramResponse(
            @Schema(example = "4") Long id,
            @Schema(example = "3") Long departmentId,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(example = "BTECH-CSE") String code,
            @Schema(example = "Bachelor of Technology in CSE") String name,
            @Schema(allowableValues = {"UNDERGRADUATE", "POSTGRADUATE", "DIPLOMA", "DOCTORATE", "CERTIFICATE"},
                    example = "UNDERGRADUATE") String degreeType,
            @Schema(example = "8") int durationSemesters,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status) {

        static ProgramResponse of(Program program) {
            Department department = program.getDepartment();
            return new ProgramResponse(program.getId(), department.getId(), department.getCode(), department.getName(),
                    program.getCode(), program.getName(), program.getDegreeType().name(), program.getDurationSemesters(),
                    program.getStatus().name());
        }
    }

    public record AcademicSessionResponse(
            @Schema(example = "2") Long id,
            @Schema(example = "2026-27") String code,
            @Schema(example = "2026-07-01") LocalDate startDate,
            @Schema(example = "2027-06-30") LocalDate endDate,
            @Schema(allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "PLANNED") String status,
            @Schema(description = "Whether this is the college's current session. At most one is.") boolean current) {

        static AcademicSessionResponse of(AcademicSession session) {
            return new AcademicSessionResponse(session.getId(), session.getCode(), session.getStartDate(),
                    session.getEndDate(), session.getStatus().name(), session.isCurrent());
        }
    }

    public record SemesterResponse(
            @Schema(example = "9") Long id,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "Bachelor of Technology in CSE") String programName,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "2") Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}, example = "PLANNED") String status) {

        static SemesterResponse of(Semester semester) {
            Program program = semester.getProgram();
            AcademicSession session = semester.getAcademicSession();
            return new SemesterResponse(semester.getId(), program.getId(), program.getCode(), program.getName(),
                    program.getDepartment().getCode(), session.getId(), session.getCode(), semester.getSemesterNumber(),
                    semester.getStatus().name());
        }
    }

    public record CourseResponse(
            @Schema(example = "12") Long id,
            @Schema(example = "3") Long departmentId,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(example = "CS301") String code,
            @Schema(example = "Data Structures") String name,
            @Schema(description = "A short form such as DSA, or null.", example = "DSA") String shortName,
            @Schema(description = "Plain text, or null when none was entered.") String description,
            @Schema(description = "An exact decimal with two places.", example = "4.00") BigDecimal credits,
            @Schema(allowableValues = {"CORE", "ELECTIVE", "LAB", "PROJECT", "AUDIT"}, example = "CORE") String courseType,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status) {

        static CourseResponse of(Course course) {
            Department department = course.getDepartment();
            return new CourseResponse(course.getId(), department.getId(), department.getCode(), department.getName(),
                    course.getCode(), course.getName(), course.getShortName(), course.getDescription(), course.getCredits(),
                    course.getCourseType().name(), course.getStatus().name());
        }
    }

    public record SectionResponse(
            @Schema(example = "21") Long id,
            @Schema(example = "9") Long semesterId,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "4") Long programId,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "2") Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "A") String code,
            @Schema(description = "An optional longer name, or null.", example = "Section A") String name,
            @Schema(description = "The most enrolled students it holds, or null for no limit.", example = "60") Integer capacity,
            @Schema(description = "How many students are enrolled in it now.", example = "42") long enrolledCount,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE") String status) {

        static SectionResponse of(Section section, long enrolledCount) {
            Semester semester = section.getSemester();
            Program program = semester.getProgram();
            AcademicSession session = semester.getAcademicSession();
            return new SectionResponse(section.getId(), semester.getId(), semester.getSemesterNumber(), program.getId(),
                    program.getCode(), session.getId(), session.getCode(), section.getCode(), section.getName(),
                    section.getCapacity(), enrolledCount, section.getStatus().name());
        }
    }
}
