package com.stacc.backend.lms.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record LmsPageLink(
        @Schema(example = "32")
        Long id,
        @Schema(example = "Singly linked lists")
        String title,
        @Schema(example = "Unit 1: Arrays and Linked Lists")
        String moduleTitle) {
}
