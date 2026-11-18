package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.content.LmsMaterialFileType;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.Set;

public record AiCourseKnowledge(String fingerprint, List<Topic> topics, List<Material> materials) {

    static final Set<LmsMaterialFileType> READABLE = EnumSet.of(
            LmsMaterialFileType.PDF, LmsMaterialFileType.TXT, LmsMaterialFileType.DOCX,
            LmsMaterialFileType.PPTX, LmsMaterialFileType.XLSX);

    public AiCourseKnowledge {
        topics = List.copyOf(topics);
        materials = List.copyOf(materials);
    }

    public record Topic(long id, long moduleId, String title, String content) {

        public boolean hasText() {
            return content != null && !content.isBlank();
        }
    }

    public record Material(long id, long topicId, String title, String fileName, String contentType, long sizeBytes,
            String objectKey) {

        public Optional<String> readableType() {
            for (LmsMaterialFileType type : READABLE) {
                if (type.contentType().equals(contentType)) {
                    return Optional.of(type.name());
                }
            }
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "Material[id=" + id + ", topicId=" + topicId + "]";
        }
    }

    public int publishedSourceCount() {
        return topics.size() + materials.size();
    }

    public boolean hasIndexableContent() {
        return topics.stream().anyMatch(Topic::hasText)
                || materials.stream().anyMatch(material -> material.readableType().isPresent());
    }

    public List<AiServiceMessages.SourceRef> allowedSources() {
        return Stream.concat(
                topics.stream().map(topic -> new AiServiceMessages.SourceRef("TOPIC", topic.id())),
                materials.stream().map(material -> new AiServiceMessages.SourceRef("STUDY_MATERIAL", material.id()))).toList();
    }

    public boolean hasModule(long moduleId) {
        return topics.stream().anyMatch(topic -> topic.moduleId() == moduleId);
    }

    public List<AiServiceMessages.SourceRef> allowedSourcesInModule(long moduleId) {
        Set<Long> topicIds = new java.util.HashSet<>();
        topics.stream().filter(topic -> topic.moduleId() == moduleId).forEach(topic -> topicIds.add(topic.id()));
        return Stream.concat(
                topics.stream().filter(topic -> topicIds.contains(topic.id()))
                        .map(topic -> new AiServiceMessages.SourceRef("TOPIC", topic.id())),
                materials.stream().filter(material -> topicIds.contains(material.topicId()))
                        .map(material -> new AiServiceMessages.SourceRef("STUDY_MATERIAL", material.id()))).toList();
    }

    public Optional<Topic> topic(long topicId) {
        return topics.stream().filter(topic -> topic.id() == topicId).findFirst();
    }

    public Optional<Material> material(long materialId) {
        return materials.stream().filter(material -> material.id() == materialId).findFirst();
    }
}
