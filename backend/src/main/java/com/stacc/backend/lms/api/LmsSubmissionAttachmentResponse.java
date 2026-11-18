package com.stacc.backend.lms.api;

import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

public record LmsSubmissionAttachmentResponse(
        @Schema(description = "Used to download the file.", example = "31")
        Long attachmentId,

        @Schema(example = "solution.pdf")
        String originalFileName,

        @Schema(example = "application/pdf")
        String contentType,

        @Schema(example = "1468006")
        long sizeBytes) {

    static LmsSubmissionAttachmentResponse of(LmsSubmissionAttachment attachment) {
        return new LmsSubmissionAttachmentResponse(attachment.getId(), attachment.getOriginalFileName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }
}
