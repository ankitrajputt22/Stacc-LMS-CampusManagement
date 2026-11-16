package com.stacc.backend.lms.ai.flashcards;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The 20th set is taken once under the account lock, two reviews of one card both count, and deleting removes the cards. */
class AiFlashcardMySqlIT extends MySqlIntegrationTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");

    @Autowired
    private AiFlashcardStore store;

    @Autowired
    private AiFlashcardSetRepository sets;

    @Autowired
    private AiFlashcardRepository cards;

    @Autowired
    private LmsCourseRepository courses;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private LmsStudentAccessService studentAccess;

    private static List<AiFlashcardStore.CardDraft> oneCard() {
        return List.of(new AiFlashcardStore.CardDraft("Front", "Back", "TOPIC", 1L, "Topic", 1L));
    }

    @Test
    void setsStayWithinTheQuotaAndReviewsNeverLoseEachOther() throws Exception {
        Long student = accounts.findByLoginId("student1").orElseThrow().getId();
        Long course = courses.findAll().stream().map(LmsCourse::getId)
                .filter(id -> studentAccess.canAccessCourse(student, id)).findFirst().orElseThrow();
        for (long i = sets.countByLmsCourseIdAndUserAccountId(course, student); i < AiFlashcardStore.MAX_SETS - 1; i++) {
            store.create(student, course, "Set " + i, oneCard(), TODAY);
        }
        List<Concurrently.Outcome<Long>> outcomes = Concurrently.run(
                () -> store.create(student, course, "First", oneCard(), TODAY).getId(),
                () -> store.create(student, course, "Second", oneCard(), TODAY).getId());
        assertThat(outcomes.stream().filter(outcome -> outcome.result() != null)).hasSize(1);
        assertThat(outcomes.stream().map(Concurrently.Outcome::failure).filter(ApiException.class::isInstance)).hasSize(1);
        assertThat(sets.countByLmsCourseIdAndUserAccountId(course, student)).isEqualTo(AiFlashcardStore.MAX_SETS);

        Long setId = outcomes.stream().map(Concurrently.Outcome::result).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        Long cardId = store.get(student, course, setId).getCards().get(0).getId();
        Concurrently.run(
                () -> store.review(student, course, setId, cardId, ReviewRating.GOOD, TODAY, Instant.now()).getId(),
                () -> store.review(student, course, setId, cardId, ReviewRating.GOOD, TODAY, Instant.now()).getId());
        assertThat(cards.findById(cardId).orElseThrow().getRepetitions()).isEqualTo(2);

        store.delete(student, course, setId);
        assertThat(cards.findById(cardId)).isEmpty();
    }
}
