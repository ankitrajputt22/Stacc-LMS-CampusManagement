package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.lms.gradebook.GradeItemType;
import com.stacc.backend.lms.gradebook.GradebookBuilder;
import com.stacc.backend.lms.gradebook.LmsGradebookService;
import io.swagger.v3.oas.annotations.media.Schema;

/** A course's gradebook for the faculty who teach it: the columns and every current student's marks, by college ID. */
public record LmsGradebookResponse(List<Column> columns, List<StudentRow> students) {

    public record Column(
            GradeItemType type,
            @Schema(description = "The assignment, quiz, or manual item ID; with the type, it names the column.")
            Long sourceId,
            String title,
            BigDecimal maxMarks,
            Instant dueAt,
            boolean published,
            Instant publishedAt) {
    }

    public record Cell(
            @Schema(description = "Null until graded.")
            BigDecimal marks,
            @Schema(description = "GRADED, NEEDS_GRADING (handed in, not marked), or NO_MARKS.")
            GradebookBuilder.CellStatus status,
            String feedback) {
    }

    public record StudentRow(
            Long courseEnrollmentId,
            @Schema(description = "The student's college ID, the login ID of their account.")
            String studentLoginId,
            @Schema(description = "One cell per column, in column order.")
            List<Cell> cells,
            @Schema(description = "The marks of the graded cells.")
            BigDecimal totalMarks,
            @Schema(description = "The maximum of the graded cells' columns.")
            BigDecimal totalOutOf) {
    }

    static LmsGradebookResponse of(LmsGradebookService.FacultyGradebook book) {
        GradebookBuilder.Gradebook gradebook = book.gradebook();
        List<Column> columns = gradebook.columns().stream().map(LmsGradebookResponse::column).toList();
        List<StudentRow> rows = new ArrayList<>();
        for (CourseEnrollment enrollment : book.students()) {
            GradebookBuilder.Total total = gradebook.total(enrollment.getId(), column -> true);
            rows.add(new StudentRow(enrollment.getId(), enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId(),
                    gradebook.cells().get(enrollment.getId()).stream()
                            .map(cell -> new Cell(cell.marks(), cell.status(), cell.feedback())).toList(),
                    total.marks(), total.outOf()));
        }
        return new LmsGradebookResponse(columns, List.copyOf(rows));
    }

    static Column column(GradebookBuilder.Column column) {
        return new Column(column.type(), column.sourceId(), column.title(), column.maxMarks(), column.dueAt(), column.published(),
                column.item() == null ? null : column.item().getPublishedAt());
    }
}
