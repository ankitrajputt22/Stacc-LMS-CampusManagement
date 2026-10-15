package com.stacc.backend.lms.content;

import java.util.Set;

/**
 * The one place that says which modules and topics each side sees. Queries may narrow by status
 * first, but these rules decide.
 */
public final class LmsContentPolicy {

    /** What faculty work with: everything that is not archived. */
    public static final Set<LmsContentStatus> IN_USE = Set.of(LmsContentStatus.DRAFT, LmsContentStatus.PUBLISHED);

    /** What students may see, once they may use the course itself. */
    public static final Set<LmsContentStatus> PUBLISHED_ONLY = Set.of(LmsContentStatus.PUBLISHED);

    private LmsContentPolicy() {
    }

    /** A module is shown to students only when it is published. */
    public static boolean isVisibleToStudents(LmsModule module) {
        return module.getStatus() == LmsContentStatus.PUBLISHED;
    }

    /**
     * A topic is shown to students only when both the topic and its module are published. A published
     * topic in a draft or archived module stays hidden.
     */
    public static boolean isVisibleToStudents(LmsTopic topic) {
        return topic.getStatus() == LmsContentStatus.PUBLISHED && isVisibleToStudents(topic.getLmsModule());
    }

    /** Faculty see and manage everything that is not archived. Archived content is kept but not listed. */
    public static boolean isInUse(LmsContentStatus status) {
        return IN_USE.contains(status);
    }
}
