package com.stacc.backend.lms.content;

import java.io.IOException;
import java.io.InputStream;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks an uploaded file before anything is stored: it must not be empty, must fit the size limit, must
 * have an allowed extension, must not claim a different type, and must start the way that kind of file
 * starts. This is a strict allow-list, not a virus scan; scanning can be added for production later.
 */
public final class LmsMaterialFiles {

    static final String NO_FILE = "Choose a file to upload.";
    static final String EMPTY_FILE = "The file is empty.";
    static final String TYPE_NOT_ALLOWED =
            "This type of file is not allowed. Upload a PDF, Word, PowerPoint, Excel, text, PNG, or JPEG file.";
    static final String CONTENT_MISMATCH = "The file's contents do not match its type.";
    static final String UNREADABLE = "The file could not be read.";

    private static final int HEAD_LENGTH = 8192;

    private LmsMaterialFiles() {
    }

    /** A checked upload: its cleaned-up name, its kind, and its size. */
    public record CheckedFile(String fileName, LmsMaterialFileType type, long sizeBytes) {
    }

    public static CheckedFile check(MultipartFile file, long maxBytes) {
        if (file == null) {
            throw badRequest(NO_FILE);
        }
        if (file.isEmpty() || file.getSize() <= 0) {
            throw badRequest(EMPTY_FILE);
        }
        if (file.getSize() > maxBytes) {
            throw badRequest(tooLarge(maxBytes));
        }

        String fileName = cleanFileName(file.getOriginalFilename());
        LmsMaterialFileType type = LmsMaterialFileType.forExtension(extensionOf(fileName))
                .orElseThrow(() -> badRequest(TYPE_NOT_ALLOWED));
        if (!type.acceptsDeclaredType(file.getContentType())) {
            throw badRequest(TYPE_NOT_ALLOWED);
        }

        byte[] head = new byte[HEAD_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, HEAD_LENGTH);
        } catch (IOException failure) {
            throw badRequest(UNREADABLE);
        }
        if (!type.matchesContent(head, read)) {
            throw badRequest(CONTENT_MISMATCH);
        }
        return new CheckedFile(fileName, type, file.getSize());
    }

    static String tooLarge(long maxBytes) {
        long megabytes = maxBytes / (1024 * 1024);
        return megabytes > 0
                ? "The file is larger than the " + megabytes + " MB limit."
                : "The file is larger than the " + maxBytes + " byte limit.";
    }

    /**
     * The name as shown to people: no folder part, no control characters, at most 255 characters with
     * the extension kept. It is only a label: it never decides where the file is stored.
     */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw badRequest(TYPE_NOT_ALLOWED);
        }
        if (name.length() > LmsStudyMaterial.MAX_FILE_NAME_LENGTH) {
            String extension = extensionOf(name);
            // Allowed extensions are a few letters long. Anything longer cannot be an allowed file.
            if (extension.isEmpty() || extension.length() > 10) {
                throw badRequest(TYPE_NOT_ALLOWED);
            }
            int keep = LmsStudyMaterial.MAX_FILE_NAME_LENGTH - extension.length() - 1;
            name = name.substring(0, keep).strip() + "." + extension;
        }
        return name;
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
