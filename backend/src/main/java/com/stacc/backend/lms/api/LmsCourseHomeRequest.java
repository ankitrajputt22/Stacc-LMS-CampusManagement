package com.stacc.backend.lms.api;

import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseHomeView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a course's home shows and its welcome message. Official course details are never part of it. */
public record LmsCourseHomeRequest(
        @Schema(description = "OVERVIEW, MODULES, or SYLLABUS.", allowableValues = {"OVERVIEW", "MODULES", "SYLLABUS"})
        @NotNull(message = "Choose what the course home shows")
        LmsCourseHomeView homeView,
        @Schema(description = "Optional formatted text shown at the top of the overview. Blank removes it. Never HTML.")
        @Size(max = LmsCourse.MAX_WELCOME_LENGTH, message = "The welcome message must be at most 5,000 characters")
        String welcomeMessage) {
}
