package com.stacc.backend.lms.content;

import java.util.Set;

public final class LmsContentPolicy {

    public static final Set<LmsContentStatus> IN_USE = Set.of(LmsContentStatus.DRAFT, LmsContentStatus.PUBLISHED);

    public static final Set<LmsContentStatus> PUBLISHED_ONLY = Set.of(LmsContentStatus.PUBLISHED);

    private LmsContentPolicy() {
    }

    public static boolean isVisibleToStudents(LmsModule module) {
        return module.getStatus() == LmsContentStatus.PUBLISHED;
    }

    public static boolean isVisibleToStudents(LmsTopic topic) {
        return topic.getStatus() == LmsContentStatus.PUBLISHED && isVisibleToStudents(topic.getLmsModule());
    }

    public static boolean isVisibleToStudents(LmsStudyMaterial material) {
        return material.getStatus() == LmsContentStatus.PUBLISHED && isVisibleToStudents(material.getLmsTopic());
    }

    public static boolean isInUseByFaculty(LmsStudyMaterial material) {
        LmsTopic topic = material.getLmsTopic();
        return isInUse(material.getStatus()) && isInUse(topic.getStatus()) && isInUse(topic.getLmsModule().getStatus());
    }

    public static boolean isInUse(LmsContentStatus status) {
        return IN_USE.contains(status);
    }
}
