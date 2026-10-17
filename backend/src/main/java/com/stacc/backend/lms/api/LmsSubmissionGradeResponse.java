package com.stacc.backend.lms.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import io.swagger.v3.oas.annotations.media.Schema;

/** The grade of one attempt. Who graded it is kept for the record but not sent. */
public record LmsSubmissionGradeResponse(
        @Schema(example = "18.00")
        BigDecimal marksAwarded,

        @Schema(description = "The assignment's maximum marks when the attempt was first graded. It does not change "
                + "if the assignment's maximum changes later.", example = "20.00")
        BigDecimal maxMarksSnapshot,

        @Schema(description = "Optional plain text with line breaks. It is never HTML.",
                example = "Good solution. Explain question 4 further.")
        String feedback,

        @Schema(description = "When the current marks and feedback were saved.", example = "2026-10-21T04:00:00Z")
        Instant gradedAt) {

    static LmsSubmissionGradeResponse of(LmsSubmissionGrade grade) {
        return grade == null
                ? null
                : new LmsSubmissionGradeResponse(grade.getMarksAwarded(), grade.getMaxMarksSnapshot(), grade.getFeedback(),
                        grade.getGradedAt());
    }
}
