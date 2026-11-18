package com.stacc.backend.lms.content;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

    private static final Set<String> UNKNOWN_TYPES = Set.of("", "application/octet-stream");

    private final String contentType;
    private final Set<String> extensions;
    private final Signature signature;

    LmsMaterialFileType(String contentType, Set<String> extensions, Signature signature) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.signature = signature;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<LmsMaterialFileType> forExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extensions.contains(normalized)).findFirst();
    }

    boolean acceptsDeclaredType(String declared) {
        String normalized = declared == null ? "" : declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return UNKNOWN_TYPES.contains(normalized) || normalized.equals(contentType)
                || (this == JPEG && normalized.equals("image/pjpeg"));
    }

    boolean matchesContent(byte[] head, int length) {
        return signature.matches(head, length);
    }

    private enum Signature {
        PDF("%PDF-".getBytes(StandardCharsets.US_ASCII)),
        PNG(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        ZIP(new byte[] {'P', 'K', 0x03, 0x04}),
        OLE(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}),
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
