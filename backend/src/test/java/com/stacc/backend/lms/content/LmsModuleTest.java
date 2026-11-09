package com.stacc.backend.lms.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.Test;

class LmsModuleTest {

    private final LmsCourse lmsCourse = ContentFixtures.lmsCourse(7);

    @Test
    void newModuleIsADraftWithItsCourseTitleAndPlace() {
        LmsModule module = new LmsModule(lmsCourse, "  Introduction  ", "  What the course covers.  ", 1);

        assertSame(lmsCourse, module.getLmsCourse());
        assertEquals("Introduction", module.getTitle());
        assertEquals("What the course covers.", module.getDescription());
        assertEquals(1, module.getPosition());
        assertEquals(LmsContentStatus.DRAFT, module.getStatus());
    }

    @Test
    void descriptionIsOptionalAndKeptAsPlainText() {
        assertNull(new LmsModule(lmsCourse, "Arrays", null, 1).getDescription());
        assertNull(new LmsModule(lmsCourse, "Arrays", "   ", 1).getDescription());
        // Text that looks like HTML is stored exactly as written; it is never interpreted.
        assertEquals("<b>Read</b> chapter 1\nthen 2",
                new LmsModule(lmsCourse, "Arrays", "<b>Read</b> chapter 1\nthen 2", 1).getDescription());
    }

    @Test
    void courseTitleAndAValidPlaceAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(null, "Arrays", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(lmsCourse, null, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(lmsCourse, "  ", null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(lmsCourse, "x".repeat(201), null, 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(lmsCourse, "Arrays", "x".repeat(2001), 1));
        assertThrows(IllegalArgumentException.class, () -> new LmsModule(lmsCourse, "Arrays", null, 0));
    }

    @Test
    void publishingAndArchivingCanBeRepeatedSafely() {
        LmsModule module = new LmsModule(lmsCourse, "Arrays", null, 1);

        module.publish();
        module.publish();
        assertEquals(LmsContentStatus.PUBLISHED, module.getStatus());

        module.archive();
        module.archive();
        assertEquals(LmsContentStatus.ARCHIVED, module.getStatus());
    }

    @Test
    void archivedModuleCannotBeEditedOrPublishedAgain() {
        LmsModule module = new LmsModule(lmsCourse, "Arrays", null, 1);
        module.archive();

        assertThrows(IllegalStateException.class, () -> module.edit("Lists", null));
        assertThrows(IllegalStateException.class, module::publish);
        assertEquals("Arrays", module.getTitle());
        assertEquals(LmsContentStatus.ARCHIVED, module.getStatus());
    }

    @Test
    void editingChangesTitleAndDescriptionButNotStatusOrPlace() {
        LmsModule module = new LmsModule(lmsCourse, "Arrays", "Old", 3);
        module.publish();

        module.edit("Arrays and lists", null);

        assertEquals("Arrays and lists", module.getTitle());
        assertNull(module.getDescription());
        assertEquals(3, module.getPosition());
        assertEquals(LmsContentStatus.PUBLISHED, module.getStatus());
    }
}
