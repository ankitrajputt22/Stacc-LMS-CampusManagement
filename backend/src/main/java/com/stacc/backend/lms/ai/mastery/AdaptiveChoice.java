package com.stacc.backend.lms.ai.mastery;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Adaptive practice (v0.15.6): which published page to practise next, weighted to weaker pages and said in words. Not
 * started pages weigh 1, others 1 minus their mastery (at least 0.05, so a mastered page still comes up now and then).
 */
public final class AdaptiveChoice {

    static final double MIN_WEIGHT = 0.05;

    /** The page chosen and why, in plain words. */
    public record Choice(long topicId, String title, String reason) {
    }

    private AdaptiveChoice() {
    }

    public static Optional<Choice> choose(List<AiMasteryService.TopicMastery> topics, RandomGenerator random) {
        if (topics.isEmpty()) {
            return Optional.empty();
        }
        double[] weights = topics.stream().mapToDouble(AdaptiveChoice::weight).toArray();
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double pick = random.nextDouble() * total;
        int index = 0;
        while (index < weights.length - 1 && pick >= weights[index]) {
            pick -= weights[index];
            index++;
        }
        AiMasteryService.TopicMastery chosen = topics.get(index);
        return Optional.of(new Choice(chosen.topicId(), chosen.title(), reason(chosen.result())));
    }

    static double weight(AiMasteryService.TopicMastery topic) {
        return topic.result().evidence() == 0 ? 1.0 : Math.max(MIN_WEIGHT, 1 - topic.result().mastery());
    }

    static String reason(Mastery.Result result) {
        if (result.evidence() == 0) {
            return "You have not practised this page yet.";
        }
        int percent = (int) Math.round(result.mastery() * 100);
        return switch (result.level()) {
            case LEARNING -> "Your recent answers here average " + percent + "%, so it needs the most work.";
            case PRACTISING -> "Your recent answers here average " + percent + "%; a little more practice should secure it.";
            default -> "You know this well (" + percent + "%); a quick check keeps it fresh.";
        };
    }
}
