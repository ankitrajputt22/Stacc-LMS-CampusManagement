package com.stacc.backend.lms.content;

import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

class LmsLearningPathTest {

    private final LmsCourse course = ContentFixtures.lmsCourse(7);
    private final LmsModule first = module(course, 11, 1, LmsContentStatus.PUBLISHED);
    private final LmsModule second = module(course, 12, 2, LmsContentStatus.PUBLISHED);
    private final LmsTopic intro = required(topic(first, 31, 1, LmsContentStatus.PUBLISHED));
    private final LmsTopic optional = topic(first, 32, 2, LmsContentStatus.PUBLISHED);
    private final LmsTopic arrays = required(topic(first, 33, 3, LmsContentStatus.PUBLISHED));
    private final LmsTopic stacks = required(topic(second, 41, 1, LmsContentStatus.PUBLISHED));

    private static LmsTopic required(LmsTopic topic) {
        topic.markRequired(true);
        return topic;
    }

    private LmsLearningPath path(Long... completed) {
        return LmsLearningPath.of(List.of(first, second), List.of(intro, optional, arrays, stacks), Set.of(completed));
    }

    @Test
    void aModuleThatRequiresThePreviousOneStaysLockedUntilItsRequiredPagesAreDone() {
        second.requirePrevious(true);

        LmsLearningPath start = path();
        assertThat(start.module(11L)).isEqualTo(new LmsLearningPath.ModuleState(false, false, null));
        assertThat(start.module(12L)).isEqualTo(new LmsLearningPath.ModuleState(true, false, "Module 11"));
        assertThat(start.next().pageId()).isEqualTo(31L);

        LmsLearningPath halfway = path(31L, 32L);
        assertThat(halfway.isOpen(12L)).as("an optional page does not count").isFalse();
        assertThat(halfway.next().pageId()).isEqualTo(33L);
        assertThat(halfway.completedRequiredPages()).isEqualTo(1);

        LmsLearningPath done = path(31L, 33L);
        assertThat(done.module(11L).completed()).isTrue();
        assertThat(done.isOpen(12L)).isTrue();
        assertThat(done.next().pageId()).isEqualTo(41L);
        assertThat(done.requiredPages()).isEqualTo(3);
    }

    @Test
    void withoutTheRequirementEveryModuleIsOpenAndANextPageIsNeverInALockedModule() {
        LmsLearningPath path = path(31L, 33L, 41L);

        assertThat(path.isOpen(12L)).isTrue();
        assertThat(path.next()).as("nothing left to do").isNull();
        assertThat(path.completedRequiredPages()).isEqualTo(3);
        assertThat(path.module(99L).locked()).as("a module the student cannot see is never open").isTrue();
    }

    @Test
    void aModuleWithoutRequiredPagesIsCompleteOnceOpenSoTheNextOneOpensToo() {
        LmsModule third = module(course, 13, 3, LmsContentStatus.PUBLISHED);
        second.requirePrevious(true);
        third.requirePrevious(true);
        LmsTopic optionalStack = topic(second, 42, 1, LmsContentStatus.PUBLISHED);

        LmsLearningPath path = LmsLearningPath.of(List.of(first, second, third), List.of(intro, arrays, optionalStack),
                Set.of(31L, 33L));

        assertThat(path.module(12L).completed()).isTrue();
        assertThat(path.isOpen(13L)).isTrue();
    }
}
