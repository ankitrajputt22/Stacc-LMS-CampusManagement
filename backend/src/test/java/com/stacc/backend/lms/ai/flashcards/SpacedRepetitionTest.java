package com.stacc.backend.lms.ai.flashcards;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SpacedRepetitionTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");

    @Test
    void goodAnswersSpreadReviewsOut() {
        SpacedRepetition.State state = new SpacedRepetition.State(SpacedRepetition.START_EASE, 0, 0, 0);
        state = SpacedRepetition.next(state, ReviewRating.GOOD);
        assertThat(state.intervalDays()).isEqualTo(1);
        state = SpacedRepetition.next(state, ReviewRating.GOOD);
        assertThat(state.intervalDays()).isEqualTo(3);
        state = SpacedRepetition.next(state, ReviewRating.GOOD);
        assertThat(state.intervalDays()).isEqualTo(8);
        assertThat(SpacedRepetition.next(state, ReviewRating.EASY).intervalDays()).isGreaterThan(SpacedRepetition.next(state, ReviewRating.GOOD).intervalDays());
    }

    @Test
    void againStartsOverTodayAndEaseStaysInBounds() {
        SpacedRepetition.State state = new SpacedRepetition.State(new BigDecimal("1.35"), 20, 5, 0);
        SpacedRepetition.State again = SpacedRepetition.next(state, ReviewRating.AGAIN);
        assertThat(again.intervalDays()).isZero();
        assertThat(again.repetitions()).isZero();
        assertThat(again.lapses()).isEqualTo(1);
        assertThat(again.ease()).isEqualByComparingTo("1.30");
        SpacedRepetition.State easy = new SpacedRepetition.State(new BigDecimal("2.95"), 300, 9, 0);
        assertThat(SpacedRepetition.next(easy, ReviewRating.EASY).ease()).isEqualByComparingTo("3.00");
        assertThat(SpacedRepetition.next(easy, ReviewRating.EASY).intervalDays()).isEqualTo(365);
    }

    @Test
    void aCardIsDueOnItsNextDay() {
        AiFlashcardSet set = new AiFlashcardSet(7L, 11L, "Joins");
        set.addCard("Inner join?", "Rows with matches in both tables.", "TOPIC", 31L, "Joins", 31L, TODAY);
        AiFlashcard card = set.getCards().get(0);
        assertThat(card.isDue(TODAY)).isTrue();
        card.review(ReviewRating.GOOD, TODAY, Instant.parse("2026-11-16T05:00:00Z"));
        assertThat(card.getDueOn()).isEqualTo(TODAY.plusDays(1));
        assertThat(card.isDue(TODAY)).isFalse();
        card.review(ReviewRating.AGAIN, TODAY, Instant.parse("2026-11-16T05:01:00Z"));
        assertThat(card.isDue(TODAY)).isTrue();
    }
}
