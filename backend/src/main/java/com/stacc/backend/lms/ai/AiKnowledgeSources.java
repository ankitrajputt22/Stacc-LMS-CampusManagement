package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.content.LmsContentPolicy;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the published content an LMS course's AI knowledge is built from, and its fingerprint. It applies the
 * student visibility rule of {@link LmsContentPolicy}, so AI knowledge can never contain anything a student of the
 * course could not open: no drafts, nothing archived, nothing under an unpublished topic or module, and never
 * submissions, grades, or any student's data. It only reads.
 */
@Service
public class AiKnowledgeSources {

    /** Changing how sources are indexed changes this, so every course then shows as needing a refresh. */
    static final String FORMAT = "stacc-ai-knowledge-v1";

    private final LmsTopicRepository topics;
    private final LmsStudyMaterialRepository materials;

    public AiKnowledgeSources(LmsTopicRepository topics, LmsStudyMaterialRepository materials) {
        this.topics = topics;
        this.materials = materials;
    }

    @Transactional(readOnly = true)
    public AiCourseKnowledge publishedKnowledge(Long lmsCourseId) {
        List<LmsTopic> visibleTopics = topics
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .sorted(Comparator.comparing((LmsTopic topic) -> topic.getLmsModule().getPosition())
                        .thenComparing(topic -> topic.getLmsModule().getId())
                        .thenComparing(LmsTopic::getPosition)
                        .thenComparing(LmsTopic::getId))
                .toList();
        Map<Long, Integer> topicOrder = new HashMap<>();
        for (int index = 0; index < visibleTopics.size(); index++) {
            topicOrder.put(visibleTopics.get(index).getId(), index);
        }
        List<LmsStudyMaterial> visibleMaterials = materials
                .findAllInCourse(lmsCourseId, LmsContentPolicy.PUBLISHED_ONLY, LmsContentPolicy.PUBLISHED_ONLY,
                        LmsContentPolicy.PUBLISHED_ONLY).stream()
                .filter(LmsContentPolicy::isVisibleToStudents)
                .filter(material -> topicOrder.containsKey(material.getLmsTopic().getId()))
                .sorted(Comparator.comparing((LmsStudyMaterial material) -> topicOrder.get(material.getLmsTopic().getId()))
                        .thenComparing(LmsStudyMaterial::getPosition)
                        .thenComparing(LmsStudyMaterial::getId))
                .toList();

        List<AiCourseKnowledge.Topic> topicSources = visibleTopics.stream()
                .map(topic -> new AiCourseKnowledge.Topic(topic.getId(), moduleId(topic.getLmsModule()),
                        topic.getTitle(), topic.getContent()))
                .toList();
        List<AiCourseKnowledge.Material> materialSources = visibleMaterials.stream()
                .map(material -> new AiCourseKnowledge.Material(material.getId(), material.getLmsTopic().getId(),
                        material.getTitle(), material.getOriginalFileName(), material.getContentType(),
                        material.getSizeBytes(), material.getObjectKey()))
                .toList();
        return new AiCourseKnowledge(fingerprint(topicSources, materialSources), topicSources, materialSources);
    }

    private static long moduleId(LmsModule module) {
        return module.getId();
    }

    /**
     * The SHA-256 of what the index would contain. Sources are listed by ID, so reordering content changes nothing,
     * while publishing, archiving, or editing a topic's title or text, or a material's title, changes the result.
     * A material's file never changes once uploaded, so its key and size stand for its bytes.
     */
    static String fingerprint(List<AiCourseKnowledge.Topic> topics, List<AiCourseKnowledge.Material> materials) {
        StringBuilder text = new StringBuilder(FORMAT).append('\n');
        topics.stream().sorted(Comparator.comparingLong(AiCourseKnowledge.Topic::id)).forEach(topic -> text
                .append("T|").append(topic.id()).append('|').append(topic.moduleId())
                .append('|').append(AiCourseKeys.sha256Hex(topic.title()))
                .append('|').append(AiCourseKeys.sha256Hex(topic.content() == null ? "" : topic.content()))
                .append('\n'));
        materials.stream().sorted(Comparator.comparingLong(AiCourseKnowledge.Material::id)).forEach(material -> text
                .append("M|").append(material.id()).append('|').append(material.topicId())
                .append('|').append(AiCourseKeys.sha256Hex(material.title()))
                .append('|').append(AiCourseKeys.sha256Hex(material.fileName()))
                .append('|').append(material.contentType()).append('|').append(material.sizeBytes())
                .append('|').append(AiCourseKeys.sha256Hex(material.objectKey()))
                .append('\n'));
        return AiCourseKeys.sha256Hex(text.toString());
    }
}
