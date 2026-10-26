package com.stacc.backend.lms.ai;

import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexSource;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Packs a course's published content for the AI service: each topic's text, followed by its readable study materials,
 * whose bytes are read through {@link ObjectStorageService}. The AI service gets only these bytes, never storage
 * access, keys, or database access. Limits keep a refresh bounded: the most sources, the largest file (the
 * study-material limit), and the most bytes in total; whatever does not fit is skipped and counted.
 */
@Component
class AiIndexPayloads {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiIndexPayloads.class);

    record Payload(List<IndexSource> sources, int skipped) {
    }

    private final ObjectStorageService storage;
    private final AiProperties properties;
    private final LmsMaterialProperties materialProperties;

    AiIndexPayloads(ObjectStorageService storage, AiProperties properties, LmsMaterialProperties materialProperties) {
        this.storage = storage;
        this.properties = properties;
        this.materialProperties = materialProperties;
    }

    Payload build(AiCourseKnowledge knowledge) {
        List<IndexSource> sources = new ArrayList<>();
        int skipped = 0;
        long bytesLeft = properties.maxSyncBytes().toBytes();
        int maxSources = properties.maxSyncSources();
        long maxFileBytes = materialProperties.maxFileSize().toBytes();
        for (AiCourseKnowledge.Topic topic : knowledge.topics()) {
            if (!topic.hasText() || sources.size() >= maxSources) {
                skipped++;
            } else {
                sources.add(IndexSource.topic(topic.id(), topic.title(), topic.content()));
            }
            for (AiCourseKnowledge.Material material : knowledge.materials()) {
                if (material.topicId() != topic.id()) {
                    continue;
                }
                Optional<String> type = material.readableType();
                if (type.isEmpty() || sources.size() >= maxSources || material.sizeBytes() > maxFileBytes
                        || material.sizeBytes() > bytesLeft) {
                    skipped++;
                    continue;
                }
                byte[] bytes = read(material, maxFileBytes);
                if (bytes == null || bytes.length > bytesLeft) {
                    skipped++;
                    continue;
                }
                bytesLeft -= bytes.length;
                sources.add(IndexSource.material(material.id(), material.topicId(), material.title(),
                        material.fileName(), type.get(), Base64.getEncoder().encodeToString(bytes)));
            }
        }
        return new Payload(List.copyOf(sources), skipped);
    }

    private byte[] read(AiCourseKnowledge.Material material, long maxFileBytes) {
        try (InputStream content = storage.open(material.objectKey())) {
            byte[] bytes = content.readNBytes((int) Math.min(maxFileBytes + 1, Integer.MAX_VALUE - 8));
            return bytes.length > maxFileBytes ? null : bytes;
        } catch (IOException | RuntimeException exception) {
            // Only the material's ID is logged, never its key or contents.
            LOGGER.warn("Skipped study material {} for AI indexing: it could not be read", material.id());
            return null;
        }
    }
}
