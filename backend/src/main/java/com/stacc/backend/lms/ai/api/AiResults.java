package com.stacc.backend.lms.ai.api;

import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiServiceMessages.Citation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks what the AI service returned before a browser sees it. Citations are rebuilt from Stacc's own records and
 * kept only when they name a source of the course's current published content, so a stale, foreign, or invented
 * source can never be shown. Text is trimmed and cut to fixed lengths.
 */
final class AiResults {

    static final int ANSWER_MAX = 2000;
    static final int SUMMARY_MAX = 1500;
    static final int KEY_POINT_MAX = 300;
    static final int MAX_KEY_POINTS = 8;
    static final int QUESTION_MAX = 1500;
    static final int SUGGESTED_ANSWER_MAX = 2000;
    static final int OPTION_MAX = 300;
    static final int MAX_OPTIONS = 6;
    static final int TITLE_MAX = 200;
    static final int INSTRUCTIONS_MAX = 2000;

    private AiResults() {
    }

    static List<AiCitationResponse> citations(List<Citation> cited, AiCourseKnowledge knowledge) {
        if (cited == null) {
            return List.of();
        }
        Set<AiCitationResponse> result = new LinkedHashSet<>();
        for (Citation citation : cited) {
            if (citation == null || citation.sourceType() == null || citation.sourceId() == null) {
                continue;
            }
            Integer page = positive(citation.pageNumber());
            Integer slide = positive(citation.slideNumber());
            long id = citation.sourceId();
            if ("TOPIC".equals(citation.sourceType())) {
                knowledge.topic(id).ifPresent(topic ->
                        result.add(new AiCitationResponse("TOPIC", id, topic.title(), null, null, null)));
            } else if ("STUDY_MATERIAL".equals(citation.sourceType())) {
                knowledge.material(id).ifPresent(material -> result.add(new AiCitationResponse(
                        "STUDY_MATERIAL", id, material.title(), material.fileName(), page, slide)));
            }
        }
        return List.copyOf(result);
    }

    static String text(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    static List<String> texts(List<String> values, int maxEach, int maxCount) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String text = text(value, maxEach);
                if (text != null && result.size() < maxCount) {
                    result.add(text);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }
}
