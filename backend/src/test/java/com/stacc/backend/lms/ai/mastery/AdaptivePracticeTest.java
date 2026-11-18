package com.stacc.backend.lms.ai.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardSet;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.assignment.LmsAssignment;
import org.junit.jupiter.api.Test;

class AdaptivePracticeTest {

    private static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");

    private static AiMasteryService.TopicMastery topic(long id, String title, Mastery.Level level, double mastery, int evidence,
            Instant last) {
        return new AiMasteryService.TopicMastery(id, title, new Mastery.Result(mastery, evidence / (evidence + 3.0), level, evidence, last));
    }

    @Test
    void weakPagesComeUpFarMoreOften() {
        List<AiMasteryService.TopicMastery> topics = List.of(topic(1, "Joins", Mastery.Level.MASTERED, 0.98, 10, NOW),
                topic(2, "Indexes", Mastery.Level.LEARNING, 0.2, 5, NOW));
        Random random = new Random(7);
        long weak = java.util.stream.IntStream.range(0, 1000)
                .filter(i -> AdaptiveChoice.choose(topics, random).orElseThrow().topicId() == 2).count();
        assertThat(weak).isGreaterThan(850);
        assertThat(AdaptiveChoice.choose(List.of(), random)).isEmpty();
        assertThat(AdaptiveChoice.reason(topics.get(1).result())).isEqualTo("Your recent answers here average 20%, so it needs the most work.");
    }

    @Test
    void recommendationsFollowTheRulesAndSayWhy() {
        AiMasteryService mastery = mock(AiMasteryService.class);
        when(mastery.view(11L, 7L)).thenReturn(List.of(
                topic(1, "Joins", Mastery.Level.MASTERED, 0.9, 10, NOW.minusSeconds(30L * 86_400)),
                topic(2, "Indexes", Mastery.Level.LEARNING, 0.3, 4, NOW),
                topic(3, "Views", Mastery.Level.NOT_STARTED, 0, 0, null)));
        AiFlashcardStore flashcards = mock(AiFlashcardStore.class);
        AiFlashcardSet set = new AiFlashcardSet(7L, 11L, "Unit 2 cards");
        when(flashcards.list(any(), any(), any())).thenReturn(List.of(new AiFlashcardStore.SetSummary(set, 10, 3)));
        AiOpenAssignments assignments = mock(AiOpenAssignments.class);
        LmsAssignment soon = mock(LmsAssignment.class);
        when(soon.getId()).thenReturn(9L);
        when(soon.getTitle()).thenReturn("Lab 2");
        when(soon.getDueAt()).thenReturn(Instant.parse("2026-11-18T05:00:00Z"));
        when(assignments.stillToHandIn(11L, 7L, NOW)).thenReturn(List.of(soon));
        AiRecommendationService service = new AiRecommendationService(mastery, flashcards, assignments, new EngagementProperties("UTC"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.recommend(11L, 7L)).extracting(recommendation -> recommendation.kind() + ": " + recommendation.reason())
                .containsExactly(
                        "PRACTISE_WEAK: Your recent answers here average 30%, so it needs the most work.",
                        "REVIEW_FLASHCARDS: 3 cards are due today.",
                        "ASSIGNMENT_SOON: It is due Wed 18 Nov.",
                        "START_PAGE: You have not practised this page yet.",
                        "REFRESH_PAGE: You last practised it 30 days ago.");
    }
}
