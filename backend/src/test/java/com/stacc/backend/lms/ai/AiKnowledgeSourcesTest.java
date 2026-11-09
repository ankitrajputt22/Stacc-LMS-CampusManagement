package com.stacc.backend.lms.ai;

import static com.stacc.backend.lms.content.LmsContentStatus.ARCHIVED;
import static com.stacc.backend.lms.content.LmsContentStatus.DRAFT;
import static com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsStudyMaterial;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopic;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Which content AI knowledge is built from. The repositories here return every row whatever its status, so the test
 * proves the service applies the student visibility rule itself.
 */
class AiKnowledgeSourcesTest {

    private final LmsTopicRepository topicRepository = mock(LmsTopicRepository.class);
    private final LmsStudyMaterialRepository materialRepository = mock(LmsStudyMaterialRepository.class);
    private final AiKnowledgeSources sources = new AiKnowledgeSources(topicRepository, materialRepository);

    private final List<LmsTopic> topics = new ArrayList<>();
    private final List<LmsStudyMaterial> materials = new ArrayList<>();
    private LmsTopic publishedA;
    private LmsTopic draftB;
    private LmsStudyMaterial draftE;

    @BeforeEach
    void course() {
        LmsCourse course = ContentFixtures.lmsCourse(1);
        LmsModule published = ContentFixtures.module(course, 10, 1, PUBLISHED);
        LmsModule draftModule = ContentFixtures.module(course, 11, 2, DRAFT);
        publishedA = ContentFixtures.topic(published, 1, 1, PUBLISHED);
        draftB = ContentFixtures.topic(published, 2, 2, DRAFT);
        LmsTopic archivedC = ContentFixtures.topic(published, 3, 3, ARCHIVED);
        LmsTopic publishedInDraftModule = ContentFixtures.topic(draftModule, 4, 1, PUBLISHED);
        ReflectionTestUtils.setField(draftB, "content", "SECRET-DRAFT-PHRASE-7781 only in a draft topic.");
        topics.addAll(List.of(publishedA, draftB, archivedC, publishedInDraftModule));
        LmsStudyMaterial publishedD = ContentFixtures.material(publishedA, 20, 1, PUBLISHED);
        draftE = ContentFixtures.material(publishedA, 21, 2, DRAFT);
        LmsStudyMaterial publishedUnderDraftTopic = ContentFixtures.material(draftB, 22, 1, PUBLISHED);
        materials.addAll(List.of(publishedD, draftE, publishedUnderDraftTopic));
        when(topicRepository.findAllInCourse(eq(1L), any(), any())).thenAnswer(invocation -> List.copyOf(topics));
        when(materialRepository.findAllInCourse(eq(1L), any(), any(), any())).thenAnswer(invocation -> List.copyOf(materials));
    }

    @Test
    void onlyPublishedVisibleContentIsIncluded() {
        AiCourseKnowledge knowledge = sources.publishedKnowledge(1L);
        assertThat(knowledge.topics()).extracting(AiCourseKnowledge.Topic::id).containsExactly(1L);
        assertThat(knowledge.materials()).extracting(AiCourseKnowledge.Material::id).containsExactly(20L);
        assertThat(knowledge.publishedSourceCount()).isEqualTo(2);
        assertThat(knowledge.topics()).noneMatch(topic -> topic.content().contains("SECRET-DRAFT-PHRASE"));
        assertThat(knowledge.fingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void editingPublishedContentChangesTheFingerprint() {
        String before = sources.publishedKnowledge(1L).fingerprint();
        assertThat(sources.publishedKnowledge(1L).fingerprint()).isEqualTo(before);

        publishedA.edit("Topic 1", "Changed text of topic 1");
        String edited = sources.publishedKnowledge(1L).fingerprint();
        assertThat(edited).isNotEqualTo(before);

        draftB.publish();
        String published = sources.publishedKnowledge(1L).fingerprint();
        assertThat(published).isNotEqualTo(edited);

        draftE.publish();
        assertThat(sources.publishedKnowledge(1L).fingerprint()).isNotEqualTo(published);
    }

    @Test
    void draftChangesAndReorderingLeaveTheFingerprintAlone() {
        String before = sources.publishedKnowledge(1L).fingerprint();
        draftB.edit("Renamed draft", "More draft text");
        draftE.edit("Renamed draft material", null);
        publishedA.moveTo(5);
        assertThat(sources.publishedKnowledge(1L).fingerprint()).isEqualTo(before);
    }

    @Test
    void readableFileKinds() {
        AiCourseKnowledge.Material pdf = new AiCourseKnowledge.Material(1, 1, "N", "n.pdf", "application/pdf", 1, "k");
        AiCourseKnowledge.Material png = new AiCourseKnowledge.Material(2, 1, "I", "i.png", "image/png", 1, "k");
        AiCourseKnowledge.Material doc = new AiCourseKnowledge.Material(3, 1, "D", "d.doc", "application/msword", 1, "k");
        assertThat(pdf.readableType()).contains("PDF");
        assertThat(png.readableType()).isEmpty();
        assertThat(doc.readableType()).isEmpty();
        assertThat(pdf.toString()).doesNotContain("k]").doesNotContain("objectKey");
    }
}
