package com.stacc.backend.lms.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new order of a module's topics: every topic that is not archived, each exactly once. */
public record LmsTopicOrderRequest(
        @ArraySchema(arraySchema = @Schema(description = "Topic IDs in their new order.", example = "[31, 30, 34]"))
        @NotNull(message = "Topic IDs are required")
        @Size(max = 500, message = "Too many topic IDs")
        List<@NotNull(message = "Topic IDs cannot be empty") Long> topicIds) {
}
