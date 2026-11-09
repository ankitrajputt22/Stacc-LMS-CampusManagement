package com.stacc.backend.lms.ai;

import com.stacc.backend.lms.content.LmsMaterialFileType;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The published content of one LMS course that its AI knowledge is built from: topics and study materials that
 * students can see (the material, its topic, and its module all published), in course order, with the fingerprint
 * of exactly that content. Drafts and archived content are never part of it.
 *
 * @param fingerprint the SHA-256 of the content, as 64 lower-case hex characters
 * @param topics      the published topics, including ones without text (they are skipped when indexing)
 * @param materials   the published study materials, including file kinds that cannot be read (also skipped)
 */
public record AiCourseKnowledge(String fingerprint, List<Topic> topics, List<Material> materials) {

    /** The file kinds whose text the AI service can extract. Images and old binary Office files are skipped. */
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

    /** A material's details. The object key is internal: it is used to read the file and never leaves the backend. */
    public record Material(long id, long topicId, String title, String fileName, String contentType, long sizeBytes,
            String objectKey) {

        /** The AI service's name for this file kind, when its text can be extracted. */
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

    /** Every published topic and study material, readable or not. */
    public int publishedSourceCount() {
        return topics.size() + materials.size();
    }

    /** Whether anything could give the AI text: a topic with text or a readable study material. */
    public boolean hasIndexableContent() {
        return topics.stream().anyMatch(Topic::hasText)
                || materials.stream().anyMatch(material -> material.readableType().isPresent());
    }

    public Optional<Topic> topic(long topicId) {
        return topics.stream().filter(topic -> topic.id() == topicId).findFirst();
    }

    public Optional<Material> material(long materialId) {
        return materials.stream().filter(material -> material.id() == materialId).findFirst();
    }
}
