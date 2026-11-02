package com.stacc.backend.academic.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The signed-in person's own academic profile: a part for each of the student and faculty roles the account holds
 * with a profile behind it, and null for the others. Only official data, and only published results; never GPA, ranks,
 * or pass or fail.
 */
public record MyAcademicProfileResponse(
        @Schema(description = "Null when the account holds no student role or has no student profile.") StudentPart student,
        @Schema(description = "Null when the account holds no faculty role or has no faculty profile.") FacultyPart faculty) {

    public record StudentPart(
            @Schema(description = "The student ID.", example = "student1") String loginId,
            @Schema(description = "The roll number, or null.") String rollNumber,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "Bachelor of Technology in CSE") String programName,
            @Schema(example = "UNDERGRADUATE") String degreeType,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(description = "The session of original admission.", example = "2025-26") String admissionSessionCode,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status,
            @Schema(description = "Where they study now, from their enrolled semester enrollment, or null.") Current current,
            @Schema(description = "Every semester enrollment, the latest session first.") List<SemesterRecord> semesters,
            @Schema(description = "The name of who heads the program's department now, or null when nobody does.")
            String headOfDepartment) {
    }

    public record Current(
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "Or null when not placed in a section.", example = "A") String sectionCode) {
    }

    public record SemesterRecord(
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(description = "Or null.", example = "A") String sectionCode,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}) String status,
            @Schema(description = "The course enrollments of the semester, by course code.") List<CourseRecord> courses) {
    }

    public record CourseRecord(
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4.00") BigDecimal credits,
            @Schema(allowableValues = {"CORE", "ELECTIVE", "LAB", "PROJECT", "AUDIT"}) String courseType,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN"}) String status,
            @Schema(description = "The published result, or null when none is published.") Result result) {
    }

    public record Result(
            @Schema(example = "86.00") BigDecimal marksObtained,
            @Schema(example = "100.00") BigDecimal maxMarks,
            @Schema(description = "Or null when none was entered.", example = "A") String gradeCode,
            Instant publishedAt) {
    }

    public record FacultyPart(
            @Schema(description = "The employee ID.", example = "faculty1") String loginId,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status,
            @Schema(description = "The course offerings they actively teach, the latest session first.") List<Teaching> teaching,
            @Schema(description = "The name of who heads their primary department now, or null when nobody does.")
            String headOfDepartment) {
    }

    public record Teaching(
            @Schema(example = "31") Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(description = "Or null for the whole semester.", example = "A") String sectionCode,
            @Schema(allowableValues = {"PRIMARY", "CO_FACULTY"}) String role) {
    }
}
