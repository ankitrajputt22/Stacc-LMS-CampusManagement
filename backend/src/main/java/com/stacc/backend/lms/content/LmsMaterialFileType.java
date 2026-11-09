package com.stacc.backend.lms.content;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The kinds of file a study material may be: common academic documents and plain images. Anything
 * else, including HTML, SVG, scripts, programs, and archives, is refused. Each kind fixes the content
 * type that is stored and sent back, so a browser's claim about the file never decides it.
 */
public enum LmsMaterialFileType {

    PDF("application/pdf", Set.of("pdf"), Signature.PDF),
    TXT("text/plain", Set.of("txt"), Signature.TEXT),
    DOC("application/msword", Set.of("doc"), Signature.OLE),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"), Signature.ZIP),
    PPT("application/vnd.ms-powerpoint", Set.of("ppt"), Signature.OLE),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx"), Signature.ZIP),
    XLS("application/vnd.ms-excel", Set.of("xls"), Signature.OLE),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx"), Signature.ZIP),
    PNG("image/png", Set.of("png"), Signature.PNG),
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), Signature.JPEG);

    /** What browsers send when they do not know a file's type. It is accepted and replaced by the real type. */
    private static final Set<String> UNKNOWN_TYPES = Set.of("", "application/octet-stream");

    private final String contentType;
    private final Set<String> extensions;
    private final Signature signature;

    LmsMaterialFileType(String contentType, Set<String> extensions, Signature signature) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.signature = signature;
    }

    /** The content type stored for this kind of file and sent with every download. */
    public String contentType() {
        return contentType;
    }

    /** The allowed kind for a lower-case file extension, if any. */
    public static Optional<LmsMaterialFileType> forExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extensions.contains(normalized)).findFirst();
    }

    /**
     * Whether the type the browser declared fits this kind. An unknown type is accepted; a different
     * known type, such as text/html for a .pdf file, is not.
     */
    boolean acceptsDeclaredType(String declared) {
        String normalized = declared == null ? "" : declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return UNKNOWN_TYPES.contains(normalized) || normalized.equals(contentType)
                || (this == JPEG && normalized.equals("image/pjpeg"));
    }

    /** Whether the first bytes of the file look like this kind of file. */
    boolean matchesContent(byte[] head, int length) {
        return signature.matches(head, length);
    }

    /** How each kind of file starts. Office files are either the old compound format or a ZIP package. */
    private enum Signature {
        PDF("%PDF-".getBytes(StandardCharsets.US_ASCII)),
        PNG(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        ZIP(new byte[] {'P', 'K', 0x03, 0x04}),
        OLE(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}),
        // Plain text has no fixed start. It must at least not contain the zero bytes of binary files.
        TEXT(null);

        private final byte[] prefix;

        Signature(byte[] prefix) {
            this.prefix = prefix;
        }

        boolean matches(byte[] head, int length) {
            if (prefix == null) {
                for (int index = 0; index < length; index++) {
                    if (head[index] == 0) {
                        return false;
                    }
                }
                return true;
            }
            if (length < prefix.length) {
                return false;
            }
            for (int index = 0; index < prefix.length; index++) {
                if (head[index] != prefix[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
