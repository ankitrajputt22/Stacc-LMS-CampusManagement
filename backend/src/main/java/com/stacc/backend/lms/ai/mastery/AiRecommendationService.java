package com.stacc.backend.lms.ai.mastery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.assignment.LmsAssignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommendations (v0.15.6): what to do next, from fixed rules that each say why, worked out on read from the student's
 * own mastery, flashcards, and the course's published assignments. No model is involved and nothing is stored or ranked.
 * At most six, in the order the rules are listed.
 */
@Service
public class AiRecommendationService {

    static final int MAX = 6;
    static final Duration ASSIGNMENT_SOON = Duration.ofDays(7);
    static final Duration REFRESH_AFTER = Duration.ofDays(21);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    public enum Kind { PRACTISE_WEAK, START_PAGE, REVIEW_FLASHCARDS, ASSIGNMENT_SOON, REFRESH_PAGE }

    /** One suggestion: what, about which record (a page, a set, or an assignment), and why. */
    public record Recommendation(Kind kind, Long targetId, String title, String reason) {
    }

    private final AiMasteryService mastery;
    private final AiFlashcardStore flashcards;
    private final AiOpenAssignments openAssignments;
    private final EngagementProperties college;
    private final Clock clock;

    public AiRecommendationService(AiMasteryService mastery, AiFlashcardStore flashcards, AiOpenAssignments openAssignments,
            EngagementProperties college, Clock clock) {
        this.mastery = mastery;
        this.flashcards = flashcards;
        this.openAssignments = openAssignments;
        this.college = college;
        this.clock = clock;
    }

    /** The student's recommendations in the course; the mastery view checks course access first. */
    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Long accountId, Long lmsCourseId) {
        List<AiMasteryService.TopicMastery> topics = mastery.view(accountId, lmsCourseId);
        Instant now = clock.instant();
        ZoneId zone = college.zone();
        List<Recommendation> result = new ArrayList<>();
        // 1. Pages the student is weakest at, weakest first.
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.LEARNING)
                .sorted((first, second) -> Double.compare(first.result().mastery(), second.result().mastery()))
                .limit(2)
                .forEach(topic -> result.add(new Recommendation(Kind.PRACTISE_WEAK, topic.topicId(), "Practise " + topic.title(),
                        AdaptiveChoice.reason(topic.result()))));
        // 2. Flashcards due today.
        flashcards.list(accountId, lmsCourseId, now.atZone(zone).toLocalDate()).stream().filter(set -> set.due() > 0).limit(1)
                .forEach(set -> result.add(new Recommendation(Kind.REVIEW_FLASHCARDS, set.set().getId(), "Review " + set.set().getTitle(),
                        set.due() + (set.due() == 1 ? " card is" : " cards are") + " due today.")));
        // 3. Published assignments due within a week and not handed in yet.
        openAssignments.stillToHandIn(accountId, lmsCourseId, now).stream()
                .filter(assignment -> assignment.getDueAt().isBefore(now.plus(ASSIGNMENT_SOON)))
                .limit(1)
                .forEach((LmsAssignment assignment) -> result.add(new Recommendation(Kind.ASSIGNMENT_SOON, assignment.getId(),
                        "Prepare for " + assignment.getTitle(), "It is due " + DAY.format(assignment.getDueAt().atZone(zone)) + ".")));
        // 4. Pages not started yet, in course order.
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.NOT_STARTED).limit(2)
                .forEach(topic -> result.add(new Recommendation(Kind.START_PAGE, topic.topicId(), "Start " + topic.title(),
                        "You have not practised this page yet.")));
        // 5. Mastered pages not practised for three weeks.
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.MASTERED && topic.result().lastPractisedAt() != null
                        && topic.result().lastPractisedAt().isBefore(now.minus(REFRESH_AFTER)))
                .limit(1)
                .forEach(topic -> result.add(new Recommendation(Kind.REFRESH_PAGE, topic.topicId(), "Refresh " + topic.title(),
                        "You last practised it " + Duration.between(topic.result().lastPractisedAt(), now).toDays() + " days ago.")));
        return List.copyOf(result.subList(0, Math.min(MAX, result.size())));
    }
}
