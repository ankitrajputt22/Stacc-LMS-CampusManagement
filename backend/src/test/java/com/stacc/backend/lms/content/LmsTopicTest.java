package com.stacc.backend.lms.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LmsTopicTest {

    private final LmsModule module = new LmsModule(ContentFixtures.lmsCourse(7), "Introduction", null, 1);

    @Test
    void newTopicIsADraftWithItsModuleTitleAndPlace() {
        LmsTopic topic = new LmsTopic(module, " Course overview ", "Read the syllabus.", 2);

        assertSame(module, topic.getLmsModule());
        assertEquals("Course overview", topic.getTitle());
        assertEquals("Read the syllabus.", topic.getContent());
        assertEquals(2, topic.getPosition());
        assertEquals(LmsContentStatus.DRAFT, topic.getStatus());
    }

    @Test
    void contentIsOptionalPlainTextWithItsLineBreaksKept() {
        assertNull(new LmsTopic(module, "Overview", null, 1).getContent());
        assertNull(new LmsTopic(module, "Overview", " \n ", 1).getContent());
        String text = "Step 1: read.\n\nStep 2: <script>alert(1)</script> is just text.";
        assertEquals(text, new LmsTopic(module, "Overview", text, 1).getContent());
        assertEquals(20_000, new LmsTopic(module, "Overview", "x".repeat(20_000), 1).getContent().length());
    }

    @Test
    void moduleTitleAndAValidPlaceAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsTopic(null, "Overview", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsTopic(module, "", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsTopic(module, "x".repeat(201), null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsTopic(module, "Overview", "x".repeat(20_001), 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsTopic(module, "Overview", null, -1));
    }

    @Test
    void publishingAndArchivingCanBeRepeatedSafelyAndLeaveTheModuleAlone() {
        LmsTopic topic = new LmsTopic(module, "Overview", null, 1);

        topic.publish();
        topic.publish();
        assertEquals(LmsContentStatus.PUBLISHED, topic.getStatus());
        assertEquals(LmsContentStatus.DRAFT, module.getStatus());

        topic.archive();
        topic.archive();
        assertEquals(LmsContentStatus.ARCHIVED, topic.getStatus());
    }

    @Test
    void archivedTopicCannotBeEditedOrPublishedAgain() {
        LmsTopic topic = new LmsTopic(module, "Overview", "Text", 1);
        topic.archive();

        assertThrows(IllegalStateException.class, () -> topic.edit("New", null));
        assertThrows(IllegalStateException.class, topic::publish);
        assertEquals("Overview", topic.getTitle());
    }

    @Test
    void studentsSeeATopicOnlyWhenTheTopicAndItsModuleArePublished() {
        LmsTopic topic = new LmsTopic(module, "Overview", null, 1);
        topic.publish();
        assertEquals(false, LmsContentPolicy.isVisibleToStudents(topic));

        module.publish();
        assertEquals(true, LmsContentPolicy.isVisibleToStudents(topic));

        module.archive();
        assertEquals(false, LmsContentPolicy.isVisibleToStudents(topic));
    }
}
