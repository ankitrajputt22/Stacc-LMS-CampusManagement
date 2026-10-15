package com.stacc.backend.lms.content;

import static com.stacc.backend.lms.content.ContentFixtures.material;
import static com.stacc.backend.lms.content.ContentFixtures.module;
import static com.stacc.backend.lms.content.ContentFixtures.topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * The faculty member behind ACCOUNT_ID teaches course 7, and the student behind STUDENT_ID may use it.
 * Storage is a stand-in, so these tests show what is stored, under which key, and when nothing is.
 */
class LmsStudyMaterialServiceTest {

    // Made-up IDs used only by these tests.
    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    private static final Long STUDENT_ID = 12L;
    private static final Long COURSE_ID = 7L;

    private static final byte[] PDF = "%PDF-1.7\nnotes".getBytes(StandardCharsets.US_ASCII);
    private static final LmsStudyMaterialService.Reader AS_FACULTY = new LmsStudyMaterialService.Reader(false, true);
    private static final LmsStudyMaterialService.Reader AS_STUDENT = new LmsStudyMaterialService.Reader(true, false);
    private static final LmsStudyMaterialService.Reader AS_BOTH = new LmsStudyMaterialService.Reader(true, true);

    private final LmsTopicRepository topics = mock(LmsTopicRepository.class);
    private final LmsStudyMaterialRepository materials = mock(LmsStudyMaterialRepository.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final LmsStudyMaterialService service = new LmsStudyMaterialService(
            topics, materials, facultyAccess, studentAccess, storage, new LmsMaterialProperties(DataSize.ofKilobytes(1)));

    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE_ID);
    private final LmsModule module = module(course, 11, 1, LmsContentStatus.PUBLISHED);
    private final LmsTopic topic = topic(module, 31, 1, LmsContentStatus.PUBLISHED);
    private final LmsStudyMaterial first = material(topic, 7, 1, LmsContentStatus.PUBLISHED);
    private final LmsStudyMaterial second = material(topic, 3, 2, LmsContentStatus.DRAFT);
    private final LmsStudyMaterial foreign = material(
            topic(module(ContentFixtures.lmsCourse(8), 41, 1, LmsContentStatus.PUBLISHED), 51, 1, LmsContentStatus.PUBLISHED),
            60, 1, LmsContentStatus.PUBLISHED);

    @BeforeEach
    void facultyMemberWhoTeachesCourseSevenAndAStudentWhoTakesIt() {
        when(facultyAccess.canAccessCourse(ACCOUNT_ID, COURSE_ID)).thenReturn(true);
        when(studentAccess.canAccessCourse(STUDENT_ID, COURSE_ID)).thenReturn(true);
        when(topics.findWithModuleById(31L)).thenReturn(Optional.of(topic));
        for (LmsStudyMaterial material : List.of(first, second, foreign)) {
            when(materials.findWithTopicAndModuleById(material.getId())).thenReturn(Optional.of(material));
        }
        when(materials.findAllByLmsTopicIdAndStatusInOrderByPositionAscIdAsc(31L, LmsContentPolicy.IN_USE))
                .thenReturn(List.of(first, second));
        when(materials.saveAndFlush(any(LmsStudyMaterial.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    // Upload

    @Test
    void uploadStoresTheFileUnderANewKeyThenRecordsADraftAtTheEnd() throws Exception {
        LmsStudyMaterial created = service.upload(ACCOUNT_ID, 31L, " Lecture notes ", null, pdf("../Week 1 Notes.pdf"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
        verify(storage).store(key.capture(), content.capture(), eq((long) PDF.length), eq("application/pdf"));
        assertTrue(key.getValue().matches("lms/courses/7/topics/31/[0-9a-f-]{36}"), key.getValue());
        assertEquals(key.getValue(), created.getObjectKey());

        assertEquals("Lecture notes", created.getTitle());
        assertEquals("Week 1 Notes.pdf", created.getOriginalFileName());
        assertEquals("application/pdf", created.getContentType());
        assertEquals(PDF.length, created.getSizeBytes());
        assertEquals(3, created.getPosition());
        assertEquals(LmsContentStatus.DRAFT, created.getStatus());
        verify(materials).saveAndFlush(created);
    }

    @Test
    void everyUploadGetsItsOwnKeyWhateverTheFileIsCalled() {
        String firstKey = service.upload(ACCOUNT_ID, 31L, "A", null, pdf("same.pdf")).getObjectKey();
        String secondKey = service.upload(ACCOUNT_ID, 31L, "B", null, pdf("same.pdf")).getObjectKey();

        assertTrue(!firstKey.equals(secondKey) && !firstKey.contains("same"));
    }

    @Test
    void refusedFileIsNeverStored() {
        assertRefused(HttpStatus.BAD_REQUEST, LmsMaterialFiles.TYPE_NOT_ALLOWED,
                () -> service.upload(ACCOUNT_ID, 31L, "Page", null, new MockMultipartFile("file", "page.html", "text/html", PDF)));
        assertRefused(HttpStatus.BAD_REQUEST, "The file is larger than the 1024 byte limit.",
                () -> service.upload(ACCOUNT_ID, 31L, "Big", null, new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[1025])));
        assertRefused(HttpStatus.BAD_REQUEST, LmsMaterialFiles.EMPTY_FILE,
                () -> service.upload(ACCOUNT_ID, 31L, "Empty", null, new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])));

        verifyNoInteractions(storage);
        verify(materials, never()).saveAndFlush(any());
    }

    @Test
    void storageFailureRecordsNothing() {
        doThrow(new ObjectStorageException("down", null)).when(storage).store(anyString(), any(), anyLong(), anyString());

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, LmsStudyMaterialService.STORAGE_UNAVAILABLE,
                () -> service.upload(ACCOUNT_ID, 31L, "Notes", null, pdf("Notes.pdf")));

        verify(materials, never()).saveAndFlush(any());
    }

    @Test
    void storedFileIsRemovedAgainWhenRecordingItFails() {
        when(materials.saveAndFlush(any(LmsStudyMaterial.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.upload(ACCOUNT_ID, 31L, "Notes", null, pdf("Notes.pdf")));

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(storage).store(stored.capture(), any(), anyLong(), anyString());
        verify(storage).delete(stored.getValue());
    }

    @Test
    void topicOfACourseTheFacultyMemberDoesNotTeachLooksMissing() {
        for (Executable action : List.<Executable>of(
                () -> service.upload(OTHER_ACCOUNT_ID, 31L, "Notes", null, pdf("Notes.pdf")),
                () -> service.upload(ACCOUNT_ID, 999L, "Notes", null, pdf("Notes.pdf")),
                () -> service.upload(ACCOUNT_ID, null, "Notes", null, pdf("Notes.pdf")),
                () -> service.reorderMaterials(OTHER_ACCOUNT_ID, 31L, List.of(7L, 3L)))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsContentManagementService.TOPIC_UNAVAILABLE, action);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void nothingCanBeUploadedToAnArchivedTopicOrModule() {
        topic.archive();
        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.TOPIC_ARCHIVED,
                () -> service.upload(ACCOUNT_ID, 31L, "Notes", null, pdf("Notes.pdf")));

        LmsTopic inArchivedModule = topic(module(course, 12, 2, LmsContentStatus.ARCHIVED), 32, 1, LmsContentStatus.DRAFT);
        when(topics.findWithModuleById(32L)).thenReturn(Optional.of(inArchivedModule));
        assertRefused(HttpStatus.CONFLICT, LmsContentManagementService.MODULE_ARCHIVED,
                () -> service.upload(ACCOUNT_ID, 32L, "Notes", null, pdf("Notes.pdf")));
        verifyNoInteractions(storage);
    }

    // Changes

    @Test
    void editingChangesTheTextOnly() {
        LmsStudyMaterial edited = service.updateMaterial(ACCOUNT_ID, 7L, "Week 1 notes", "Updated");

        assertSame(first, edited);
        assertEquals("Week 1 notes", edited.getTitle());
        assertEquals("material-7.pdf", edited.getOriginalFileName());
        assertEquals("lms/test/key-7", edited.getObjectKey());
        assertEquals(LmsContentStatus.PUBLISHED, edited.getStatus());
    }

    @Test
    void publishingCanBeRepeatedAndDoesNotPublishTheTopic() {
        LmsTopic draftTopic = topic(module, 33, 2, LmsContentStatus.DRAFT);
        LmsStudyMaterial inDraftTopic = material(draftTopic, 8, 1, LmsContentStatus.DRAFT);
        when(materials.findWithTopicAndModuleById(8L)).thenReturn(Optional.of(inDraftTopic));

        service.publishMaterial(ACCOUNT_ID, 8L);
        service.publishMaterial(ACCOUNT_ID, 8L);

        assertEquals(LmsContentStatus.PUBLISHED, inDraftTopic.getStatus());
        assertEquals(LmsContentStatus.DRAFT, draftTopic.getStatus());
    }

    @Test
    void archivingCanBeRepeatedAndKeepsTheStoredFile() {
        service.archiveMaterial(ACCOUNT_ID, 7L);
        service.archiveMaterial(ACCOUNT_ID, 7L);

        assertEquals(LmsContentStatus.ARCHIVED, first.getStatus());
        verifyNoInteractions(storage);
        verify(materials, never()).delete(any());
    }

    @Test
    void archivedMaterialCannotBeEditedOrPublished() {
        service.archiveMaterial(ACCOUNT_ID, 7L);

        assertRefused(HttpStatus.CONFLICT, LmsStudyMaterialService.MATERIAL_ARCHIVED,
                () -> service.updateMaterial(ACCOUNT_ID, 7L, "Back", null));
        assertRefused(HttpStatus.CONFLICT, LmsStudyMaterialService.MATERIAL_ARCHIVED,
                () -> service.publishMaterial(ACCOUNT_ID, 7L));
    }

    @Test
    void materialOfAnotherCourseOrAMissingMaterialLooksTheSame() {
        for (Executable action : List.<Executable>of(
                () -> service.updateMaterial(ACCOUNT_ID, 60L, "Taken", null),
                () -> service.publishMaterial(ACCOUNT_ID, 60L),
                () -> service.archiveMaterial(ACCOUNT_ID, 60L),
                () -> service.archiveMaterial(OTHER_ACCOUNT_ID, 7L),
                () -> service.publishMaterial(ACCOUNT_ID, 999L),
                () -> service.publishMaterial(ACCOUNT_ID, null))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE, action);
        }
        assertEquals(LmsContentStatus.PUBLISHED, foreign.getStatus());
        assertEquals(LmsContentStatus.PUBLISHED, first.getStatus());
    }

    // Order

    @Test
    void materialsAreNumberedInTheRequestedOrder() {
        List<LmsStudyMaterial> ordered = service.reorderMaterials(ACCOUNT_ID, 31L, List.of(3L, 7L));

        assertEquals(List.of(second, first), ordered);
        assertEquals(1, second.getPosition());
        assertEquals(2, first.getPosition());
    }

    @Test
    void orderMustListEveryCurrentMaterialOfTheTopicExactlyOnce() {
        for (List<Long> wrong : Arrays.<List<Long>>asList(
                List.of(7L, 7L),
                List.of(7L),
                List.of(7L, 3L, 60L),
                List.of(7L, 60L),
                Arrays.asList(7L, null),
                null)) {
            assertRefused(HttpStatus.BAD_REQUEST, LmsStudyMaterialService.MATERIAL_ORDER_MISMATCH,
                    () -> service.reorderMaterials(ACCOUNT_ID, 31L, wrong));
        }
        assertEquals(1, first.getPosition());
        assertEquals(2, second.getPosition());
    }

    // Download

    private void storedContent() {
        when(storage.open(anyString())).thenAnswer(call -> new ByteArrayInputStream(PDF));
    }

    @Test
    void studentDownloadsAPublishedMaterialFromTheStoredKey() {
        storedContent();

        LmsStudyMaterialService.Download download = service.openForDownload(STUDENT_ID, AS_STUDENT, 7L);

        assertEquals("material-7.pdf", download.fileName());
        assertEquals("application/pdf", download.contentType());
        assertEquals(1007, download.sizeBytes());
        verify(storage).open("lms/test/key-7");
    }

    @Test
    void facultyMemberDownloadsADraftOfTheirOwnCourse() {
        storedContent();

        assertEquals("material-3.pdf", service.openForDownload(ACCOUNT_ID, AS_FACULTY, 3L).fileName());
    }

    @Test
    void studentCannotDownloadADraftOrAMaterialUnderAnUnpublishedTopic() {
        storedContent();
        assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE,
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 3L));

        LmsStudyMaterial underDraftTopic = material(topic(module, 34, 3, LmsContentStatus.DRAFT), 9, 1, LmsContentStatus.PUBLISHED);
        when(materials.findWithTopicAndModuleById(9L)).thenReturn(Optional.of(underDraftTopic));
        assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE,
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 9L));
    }

    @Test
    void otherPeopleAndArchivedMaterialsGetTheSameNotFound() {
        storedContent();
        first.archive();
        for (Executable action : List.<Executable>of(
                () -> service.openForDownload(OTHER_ACCOUNT_ID, AS_FACULTY, 3L),
                () -> service.openForDownload(OTHER_ACCOUNT_ID, AS_STUDENT, 3L),
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 60L),
                () -> service.openForDownload(ACCOUNT_ID, AS_FACULTY, 60L),
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 7L),
                () -> service.openForDownload(ACCOUNT_ID, AS_FACULTY, 7L),
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 999L))) {
            assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE, action);
        }
        verify(storage, never()).open(anyString());
    }

    @Test
    void aRuleTheTokenDoesNotAllowIsNeverUsed() {
        storedContent();

        // The student may use the course, but a token without the student rule cannot download as one.
        assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE,
                () -> service.openForDownload(STUDENT_ID, AS_FACULTY, 7L));
        // The faculty member teaches the course, but without the faculty rule only published files count.
        assertRefused(HttpStatus.NOT_FOUND, LmsStudyMaterialService.MATERIAL_UNAVAILABLE,
                () -> service.openForDownload(ACCOUNT_ID, AS_STUDENT, 3L));
    }

    @Test
    void anAccountThatIsBothStudentAndFacultyMayUseEitherRule() {
        storedContent();
        when(facultyAccess.canAccessCourse(STUDENT_ID, COURSE_ID)).thenReturn(false);

        assertEquals("material-7.pdf", service.openForDownload(STUDENT_ID, AS_BOTH, 7L).fileName());
        assertEquals("material-3.pdf", service.openForDownload(ACCOUNT_ID, AS_BOTH, 3L).fileName());
    }

    @Test
    void unreadableStorageIsReportedAsUnavailableStorage() {
        when(storage.open(anyString())).thenThrow(new ObjectStorageException("down", null));

        assertRefused(HttpStatus.SERVICE_UNAVAILABLE, LmsStudyMaterialService.STORAGE_UNAVAILABLE,
                () -> service.openForDownload(STUDENT_ID, AS_STUDENT, 7L));
    }
}
