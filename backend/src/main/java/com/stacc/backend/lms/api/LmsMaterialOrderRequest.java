package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new order of a topic's study materials: every material that is not archived, each exactly once. */
public record LmsMaterialOrderRequest(
        @ArraySchema(arraySchema = @Schema(description = "Material IDs in their new order.", example = "[7, 3, 9]"))
        @NotNull(message = "Material IDs are required")
        @Size(max = 500, message = "Too many material IDs")
        List<@NotNull(message = "Material IDs cannot be empty") Long> materialIds) {
}
