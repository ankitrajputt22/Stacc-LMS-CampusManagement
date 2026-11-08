package com.stacc.backend.lms.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.lms.content.ContentFixtures;
import org.junit.jupiter.api.Test;

class LmsCourseHomeTest {

    @Test
    void aCourseStartsWithTheOverviewAndKeepsAnOptionalWelcome() {
        LmsCourse course = ContentFixtures.lmsCourse(7);
        assertThat(course.getHomeView()).isEqualTo(LmsCourseHomeView.OVERVIEW);

        course.changeHome(LmsCourseHomeView.MODULES, "  ## Welcome  ");
        assertThat(course.getHomeView()).isEqualTo(LmsCourseHomeView.MODULES);
        assertThat(course.getWelcomeMessage()).isEqualTo("## Welcome");

        course.changeHome(LmsCourseHomeView.SYLLABUS, " ");
        assertThat(course.getWelcomeMessage()).isNull();
        assertThatThrownBy(() -> course.changeHome(LmsCourseHomeView.OVERVIEW, "x".repeat(5_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anArchivedCourseCannotChange() {
        LmsCourse course = ContentFixtures.lmsCourse(7);
        course.archive();

        assertThatThrownBy(() -> course.changeHome(LmsCourseHomeView.MODULES, null)).isInstanceOf(IllegalStateException.class);
    }
}
