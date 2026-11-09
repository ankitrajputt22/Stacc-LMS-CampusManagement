package com.stacc.backend.lms.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class LmsMaterialFilesTest {

    private static final long LIMIT = 1024;
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 6, 0};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};

    private static MockMultipartFile file(String name, String declaredType, byte[] content) {
        return new MockMultipartFile("file", name, declaredType, content);
    }

    private static void assertRefused(String message, MockMultipartFile file) {
        ApiException refused = assertThrows(ApiException.class, () -> LmsMaterialFiles.check(file, LIMIT));
        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void allowedDocumentsAreAcceptedWithTheContentTypeOfTheirKind() {
        Object[][] cases = {
                {"Notes.pdf", "application/pdf", PDF, LmsMaterialFileType.PDF},
                {"readme.TXT", "text/plain; charset=UTF-8", "Line one\nLine two".getBytes(StandardCharsets.UTF_8), LmsMaterialFileType.TXT},
                {"Essay.doc", "application/msword", OLE, LmsMaterialFileType.DOC},
                {"Essay.docx", "application/octet-stream", ZIP, LmsMaterialFileType.DOCX},
                {"Slides.ppt", "", OLE, LmsMaterialFileType.PPT},
                {"Slides.pptx", LmsMaterialFileType.PPTX.contentType(), ZIP, LmsMaterialFileType.PPTX},
                {"Marks.xls", "application/vnd.ms-excel", OLE, LmsMaterialFileType.XLS},
                {"Marks.xlsx", null, ZIP, LmsMaterialFileType.XLSX},
                {"Diagram.png", "image/png", PNG, LmsMaterialFileType.PNG},
                {"Photo.jpg", "image/jpeg", JPEG, LmsMaterialFileType.JPEG},
                {"Photo.JPEG", "image/pjpeg", JPEG, LmsMaterialFileType.JPEG}};
        for (Object[] row : cases) {
            LmsMaterialFiles.CheckedFile checked = LmsMaterialFiles.check(file((String) row[0], (String) row[1], (byte[]) row[2]), LIMIT);
            assertEquals(row[3], checked.type(), (String) row[0]);
            assertEquals(row[0], checked.fileName());
            assertEquals(((byte[]) row[2]).length, checked.sizeBytes());
        }
    }

    @Test
    void missingEmptyAndOversizedFilesAreRefused() {
        assertRefused(LmsMaterialFiles.NO_FILE, null);
        assertRefused(LmsMaterialFiles.EMPTY_FILE, file("Notes.pdf", "application/pdf", new byte[0]));
        assertRefused("The file is larger than the 1024 byte limit.", file("Notes.pdf", "application/pdf", new byte[1025]));
        assertEquals("The file is larger than the 25 MB limit.", LmsMaterialFiles.tooLarge(25L * 1024 * 1024));
    }

    @Test
    void activeAndUnknownKindsOfFileAreRefused() {
        for (String name : new String[] {"page.html", "page.htm", "logo.svg", "script.js", "setup.exe", "run.bat",
                "run.cmd", "run.sh", "archive.zip", "notes", "notes.", "notes.pdf.html"}) {
            assertRefused(LmsMaterialFiles.TYPE_NOT_ALLOWED, file(name, "application/octet-stream", PDF));
        }
    }

    @Test
    void declaredTypeMustNotContradictTheExtension() {
        assertRefused(LmsMaterialFiles.TYPE_NOT_ALLOWED, file("Notes.pdf", "text/html", PDF));
        assertRefused(LmsMaterialFiles.TYPE_NOT_ALLOWED, file("Notes.txt", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void contentMustStartTheWayItsKindOfFileStarts() {
        assertRefused(LmsMaterialFiles.CONTENT_MISMATCH, file("Notes.pdf", "application/pdf", PNG));
        assertRefused(LmsMaterialFiles.CONTENT_MISMATCH, file("Photo.png", "image/png", "<html>".getBytes(StandardCharsets.UTF_8)));
        assertRefused(LmsMaterialFiles.CONTENT_MISMATCH, file("Essay.docx", null, OLE));
        assertRefused(LmsMaterialFiles.CONTENT_MISMATCH, file("Notes.txt", "text/plain", new byte[] {'a', 0, 'b'}));
        assertRefused(LmsMaterialFiles.CONTENT_MISMATCH, file("Notes.pdf", "application/pdf", new byte[] {'%', 'P'}));
    }

    @Test
    void fileNameIsCleanedAndNeverKeepsAFolder() {
        assertEquals("Notes.pdf", LmsMaterialFiles.check(file("../../etc/Notes.pdf", "application/pdf", PDF), LIMIT).fileName());
        assertEquals("Notes.pdf", LmsMaterialFiles.check(file("C:\\Users\\me\\Notes.pdf", "application/pdf", PDF), LIMIT).fileName());
        assertEquals("Week 1.pdf", LmsMaterialFiles.check(file("Week\t 1.pdf\n", "application/pdf", PDF), LIMIT).fileName());

        String longName = "a".repeat(300) + ".pdf";
        String cleaned = LmsMaterialFiles.check(file(longName, "application/pdf", PDF), LIMIT).fileName();
        assertEquals(255, cleaned.length());
        assertEquals(true, cleaned.endsWith(".pdf"));
        assertRefused(LmsMaterialFiles.TYPE_NOT_ALLOWED, file("a." + "b".repeat(300), "application/pdf", PDF));
    }
}
