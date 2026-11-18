package com.stacc.backend.lms.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionRepository;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.question.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ExamPaperSetsTest {

    private static final long FACULTY = 5L;
    private static final long COURSE_ID = 7L;
    private static final LmsCourse COURSE = ContentFixtures.lmsCourse(COURSE_ID);

    private final LmsExamBlueprintRepository blueprints = mock(LmsExamBlueprintRepository.class);
    private final LmsExamPaperRepository papers = mock(LmsExamPaperRepository.class);
    private final LmsExamPaperQuestionRepository links = mock(LmsExamPaperQuestionRepository.class);
    private final LmsQuestionRepository questions = mock(LmsQuestionRepository.class);
    private final LmsFacultyAccessService access = mock(LmsFacultyAccessService.class);
    private final LmsExamPaperService service = new LmsExamPaperService(blueprints, papers, links, questions,
            mock(LmsCourseRepository.class), mock(LmsCourseOutcomeRepository.class), access, Clock.systemUTC());
    private final List<LmsExamPaperQuestion> saved = new ArrayList<>();

    private static LmsQuestion question(long id) {
        LmsQuestion question = new LmsQuestion(COURSE, 1L, QuestionFixtures.of(QuestionType.TRUE_FALSE, List.of(), true, null));
        ReflectionTestUtils.setField(question, "id", id);
        question.markReviewed();
        return question;
    }

    @BeforeEach
    void setUp() {
        LmsExamBlueprint blueprint = new LmsExamBlueprint(COURSE, 1L, "Mid-term",
                List.of(new LmsExamBlueprintRow(1, "A", QuestionType.TRUE_FALSE, null, null, null, null, 2, BigDecimal.ONE)));
        ReflectionTestUtils.setField(blueprint, "id", 9L);
        when(blueprints.findLmsCourseIdById(9L)).thenReturn(Optional.of(COURSE_ID));
        when(blueprints.findByIdForUpdate(9L)).thenReturn(Optional.of(blueprint));
        when(blueprints.findById(9L)).thenReturn(Optional.of(blueprint));
        when(access.canAccessCourse(FACULTY, COURSE_ID)).thenReturn(true);
        when(questions.findAllForCourse(COURSE_ID)).thenReturn(List.of(question(1), question(2), question(3), question(4), question(5)));
        when(papers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @Test
    void distinctSetsShareNoQuestionAndAreNamedInOrder() {
        List<LmsExamPaper> sets = service.buildPaperSets(FACULTY, 9L, null, null, 60, 2, true);
        assertThat(sets).extracting(LmsExamPaper::getTitle).containsExactly("Mid-term – Set A", "Mid-term – Set B");
        Set<Long> used = new HashSet<>();
        saved.forEach(link -> assertThat(used.add(link.getQuestion().getId())).isTrue());
        assertThat(used).hasSize(4);
    }

    @Test
    void aBankTooSmallForDistinctSetsSavesNothingAndSaysWhich() {
        assertThatThrownBy(() -> service.buildPaperSets(FACULTY, 9L, "End term", null, 60, 3, true))
                .isInstanceOfSatisfying(ApiException.class, refused -> {
                    assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(refused.getMessage()).startsWith("Set C: Row 1 (A) needs 2 questions");
                });
        verify(papers, never()).saveAndFlush(any());
        assertThat(service.buildPaperSets(FACULTY, 9L, "End term", null, 60, 3, false)).hasSize(3);
        assertThatThrownBy(() -> service.buildPaperSets(FACULTY, 9L, null, null, 60, 5, false)).isInstanceOf(ApiException.class);
    }

    @Test
    void theShortfallCountsWhatEachRowCanTakeAndChecksAccess() {
        LmsExamPaperService.BlueprintShortfall shortfall = service.shortfall(FACULTY, 9L);
        assertThat(shortfall.lmsCourseId()).isEqualTo(COURSE_ID);
        assertThat(shortfall.rows()).singleElement().satisfies(row -> {
            assertThat(row.available()).isEqualTo(5);
            assertThat(row.missing()).isZero();
        });
        assertThatThrownBy(() -> service.shortfall(6L, 9L)).isInstanceOfSatisfying(ApiException.class,
                refused -> assertThat(refused.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
