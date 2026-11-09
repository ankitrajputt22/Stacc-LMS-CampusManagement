package com.stacc.backend.lms.api;

import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseHomeView;
import io.swagger.v3.oas.annotations.media.Schema;

/** What a course's home shows and its welcome message. */
public record LmsCourseHomeResponse(
        @Schema(allowableValues = {"OVERVIEW", "MODULES", "SYLLABUS"})
        LmsCourseHomeView homeView,
        @Schema(description = "Formatted text, or null.")
        String welcomeMessage) {

    static LmsCourseHomeResponse of(LmsCourse lmsCourse) {
        return new LmsCourseHomeResponse(lmsCourse.getHomeView(), lmsCourse.getWelcomeMessage());
    }
}
