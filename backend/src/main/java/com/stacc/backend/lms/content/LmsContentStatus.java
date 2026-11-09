package com.stacc.backend.lms.content;

/** Where a module or topic is in its life. The same three steps are used for both. */
public enum LmsContentStatus {
    /** Being written by faculty. Students never see it. */
    DRAFT,
    /** Ready for students, if its course and, for a topic, its module are visible to them too. */
    PUBLISHED,
    /** Kept for history only. Students never see it, and it can no longer be changed. */
    ARCHIVED
}
