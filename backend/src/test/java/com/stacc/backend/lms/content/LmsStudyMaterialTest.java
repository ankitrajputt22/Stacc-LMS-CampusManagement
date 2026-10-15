package com.stacc.backend.lms.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LmsStudyMaterialTest {

    private final LmsTopic topic = new LmsTopic(
            new LmsModule(ContentFixtures.lmsCourse(7), "Introduction", null, 1), "Overview", null, 1);

    private LmsStudyMaterial material(String title, String description, long size, int position) {
        return new LmsStudyMaterial(topic, title, description, "lms/courses/7/topics/31/abc", "Notes.pdf",
                "application/pdf", size, position);
    }

    @Test
    void newMaterialIsADraftWithItsTopicAndFileDetails() {
        LmsStudyMaterial material = material("  Lecture notes  ", "  Read first.  ", 1_887_436, 2);

        assertSame(topic, material.getLmsTopic());
        assertEquals("Lecture notes", material.getTitle());
        assertEquals("Read first.", material.getDescription());
        assertEquals("lms/courses/7/topics/31/abc", material.getObjectKey());
        assertEquals("Notes.pdf", material.getOriginalFileName());
        assertEquals("application/pdf", material.getContentType());
        assertEquals(1_887_436, material.getSizeBytes());
        assertEquals(2, material.getPosition());
        assertEquals(LmsContentStatus.DRAFT, material.getStatus());
    }

    @Test
    void topicTitleAndStorageDetailsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsStudyMaterial(
                null, "Notes", null, "key", "Notes.pdf", "application/pdf", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> material(" ", null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> material("x".repeat(201), null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> material("Notes", "x".repeat(1001), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsStudyMaterial(
                topic, "Notes", null, " ", "Notes.pdf", "application/pdf", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsStudyMaterial(
                topic, "Notes", null, "key", null, "application/pdf", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsStudyMaterial(
                topic, "Notes", null, "key", "Notes.pdf", "", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> material("Notes", null, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> material("Notes", null, 1, 0));
    }

    @Test
    void publishingAndArchivingCanBeRepeatedAndLeaveTheTopicAlone() {
        LmsStudyMaterial material = material("Notes", null, 1, 1);

        material.publish();
        material.publish();
        assertEquals(LmsContentStatus.PUBLISHED, material.getStatus());
        assertEquals(LmsContentStatus.DRAFT, topic.getStatus());

        material.archive();
        material.archive();
        assertEquals(LmsContentStatus.ARCHIVED, material.getStatus());
        assertEquals("lms/courses/7/topics/31/abc", material.getObjectKey());
    }

    @Test
    void archivedMaterialCannotBeEditedOrPublishedAgain() {
        LmsStudyMaterial material = material("Notes", null, 1, 1);
        material.archive();

        assertThrows(IllegalStateException.class, () -> material.edit("New", null));
        assertThrows(IllegalStateException.class, material::publish);
        assertEquals("Notes", material.getTitle());
    }

    @Test
    void editingChangesTheTextButNeverTheFile() {
        LmsStudyMaterial material = material("Notes", "Old", 10, 3);

        material.edit("Week 1 notes", null);
        material.moveTo(1);

        assertEquals("Week 1 notes", material.getTitle());
        assertNull(material.getDescription());
        assertEquals(1, material.getPosition());
        assertEquals("Notes.pdf", material.getOriginalFileName());
        assertEquals(10, material.getSizeBytes());
        assertThrows(IllegalArgumentException.class, () -> material.moveTo(0));
    }

    @Test
    void studentsSeeAMaterialOnlyWhenItItsTopicAndItsModuleArePublished() {
        LmsStudyMaterial material = material("Notes", null, 1, 1);
        material.publish();
        assertEquals(false, LmsContentPolicy.isVisibleToStudents(material));

        topic.publish();
        assertEquals(false, LmsContentPolicy.isVisibleToStudents(material));

        topic.getLmsModule().publish();
        assertEquals(true, LmsContentPolicy.isVisibleToStudents(material));
        assertEquals(true, LmsContentPolicy.isInUseByFaculty(material));

        topic.archive();
        assertEquals(false, LmsContentPolicy.isVisibleToStudents(material));
        assertEquals(false, LmsContentPolicy.isInUseByFaculty(material));
    }
}
