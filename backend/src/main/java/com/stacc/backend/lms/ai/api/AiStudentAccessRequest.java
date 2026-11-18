package com.stacc.backend.lms.ai.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AiStudentAccessRequest(@Schema(example = "false") @NotNull Boolean enabled) {
}
