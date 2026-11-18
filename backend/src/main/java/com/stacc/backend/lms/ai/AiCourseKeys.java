package com.stacc.backend.lms.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AiCourseKeys {

    private AiCourseKeys() {
    }

    public static String forLmsCourse(long lmsCourseId) {
        return sha256Hex("stacc-lms-course:" + lmsCourseId).substring(0, 32);
    }

    public static String forTeacherDocuments(long lmsCourseId) {
        return sha256Hex("stacc-lms-course-teacher:" + lmsCourseId).substring(0, 32);
    }

    public static String forStudentNotes(long lmsCourseId, long accountId) {
        return sha256Hex("stacc-student-notes:" + lmsCourseId + ":" + accountId).substring(0, 32);
    }

    public static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }
}
