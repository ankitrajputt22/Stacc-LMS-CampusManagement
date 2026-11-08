package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.lms.gradebook.GradeItemType;
import com.stacc.backend.lms.gradebook.GradebookBuilder;
import com.stacc.backend.lms.gradebook.LmsStudentGradesService;
import io.swagger.v3.oas.annotations.media.Schema;

/** The signed-in student's own published grades in one course, with the total of what is graded. */
public record LmsStudentGradesResponse(List<Item> items, BigDecimal totalMarks, BigDecimal totalOutOf) {

    public record Item(GradeItemType type, String title, Instant dueAt,
            @Schema(description = "Null until graded.")
            BigDecimal marks,
            BigDecimal maxMarks,
            GradebookBuilder.CellStatus status,
            String feedback) {
    }

    static LmsStudentGradesResponse of(LmsStudentGradesService.StudentGrades grades) {
        GradebookBuilder.Gradebook gradebook = grades.gradebook();
        List<GradebookBuilder.Cell> cells = gradebook.cells().getOrDefault(grades.courseEnrollmentId(), List.of());
        List<Item> items = new ArrayList<>();
        for (int index = 0; index < gradebook.columns().size(); index++) {
            GradebookBuilder.Column column = gradebook.columns().get(index);
            GradebookBuilder.Cell cell = cells.get(index);
            items.add(new Item(column.type(), column.title(), column.dueAt(), cell.marks(), column.maxMarks(), cell.status(),
                    cell.feedback()));
        }
        GradebookBuilder.Total total = gradebook.total(grades.courseEnrollmentId(), column -> true);
        return new LmsStudentGradesResponse(List.copyOf(items), total.marks(), total.outOf());
    }
}
