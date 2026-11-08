package com.stacc.backend.lms.api;

import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.lms.course.LmsCourse;
import io.swagger.v3.oas.annotations.media.Schema;

/** An earlier offering of the same course the faculty member taught, whose content can be copied. */
public record LmsCopySourceResponse(
        @Schema(example = "17")
        Long lmsCourseId,
        @Schema(example = "CS301")
        String courseCode,
        @Schema(example = "2025-26")
        String academicSessionCode,
        @Schema(example = "3")
        int semesterNumber,
        @Schema(example = "BTECH-CSE")
        String programCode,
        @Schema(description = "Or null for the whole semester.", example = "A")
        String sectionCode,
        @Schema(allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"})
        String lmsCourseStatus) {

    static LmsCopySourceResponse of(LmsCourse lmsCourse) {
        CourseOffering offering = lmsCourse.getCourseOffering();
        Semester semester = offering.getSemester();
        return new LmsCopySourceResponse(lmsCourse.getId(), offering.getCourse().getCode(),
                semester.getAcademicSession().getCode(), semester.getSemesterNumber(), semester.getProgram().getCode(),
                offering.getSection() == null ? null : offering.getSection().getCode(), lmsCourse.getStatus().name());
    }
}
