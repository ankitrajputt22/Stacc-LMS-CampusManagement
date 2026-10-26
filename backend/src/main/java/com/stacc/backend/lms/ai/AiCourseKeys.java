package com.stacc.backend.lms.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The opaque key under which the AI service keeps one LMS course's index. It is derived on the server from a course
 * the caller was already allowed to use, is always 32 lower-case hex characters, and never reaches a browser.
 */
public final class AiCourseKeys {

    private AiCourseKeys() {
    }

    public static String forLmsCourse(long lmsCourseId) {
        return sha256Hex("stacc-lms-course:" + lmsCourseId).substring(0, 32);
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }
}
