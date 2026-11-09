package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new order of a course's modules: every module that is not archived, each exactly once. */
public record LmsModuleOrderRequest(
        @ArraySchema(arraySchema = @Schema(description = "Module IDs in their new order.", example = "[12, 9, 15]"))
        @NotNull(message = "Module IDs are required")
        @Size(max = 500, message = "Too many module IDs")
        List<@NotNull(message = "Module IDs cannot be empty") Long> moduleIds) {
}
