package com.stacc.backend.campus.notices;

/** A notice starts DRAFT, may be PUBLISHED, and ends ARCHIVED. Nothing goes back. */
public enum CampusNoticeStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
