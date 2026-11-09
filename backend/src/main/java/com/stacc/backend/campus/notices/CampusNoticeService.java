package com.stacc.backend.campus.notices;

import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.campus.CampusNotifications;
import com.stacc.backend.campus.notices.CampusNoticeResponses.AdminCampusNoticeResponse;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins' changes to campus notices: create as a draft, correct, publish, and archive. Each change locks the notice row,
 * so two publishes at once notify once. Only the first publication notifies, through the Inbox; drafts, edits, repeated
 * publishes, and archiving do not. There is no delete, restore, or scheduling.
 */
@Service
@Transactional
public class CampusNoticeService {

    static final String NOT_FOUND = "Campus notice not found.";
    static final String ARCHIVED_EDIT = "An archived campus notice cannot be edited.";
    static final String ARCHIVED_PUBLISH = "An archived campus notice cannot be published.";

    private final CampusNoticeRepository notices;
    private final CampusNotifications notifications;
    private final Clock clock;

    public CampusNoticeService(CampusNoticeRepository notices, CampusNotifications notifications, Clock clock) {
        this.notices = notices;
        this.notifications = notifications;
        this.clock = clock;
    }

    public AdminCampusNoticeResponse create(CampusNoticeInput input) {
        CampusNotice notice;
        try {
            notice = new CampusNotice(input.content());
        } catch (IllegalArgumentException invalid) {
            throw invalid(invalid);
        }
        return AdminCampusNoticeResponse.of(notices.saveAndFlush(notice));
    }

    /** Corrects a draft or published notice; a published one stays published and keeps its publication time. */
    public AdminCampusNoticeResponse update(Long noticeId, CampusNoticeInput input) {
        CampusNotice notice = locked(noticeId);
        if (notice.getStatus() == CampusNoticeStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_EDIT);
        }
        try {
            notice.edit(input.content());
        } catch (IllegalArgumentException invalid) {
            throw invalid(invalid);
        }
        return AdminCampusNoticeResponse.of(notices.saveAndFlush(notice));
    }

    /** Publishes a draft and notifies its readers once. Publishing a published notice changes nothing. */
    public AdminCampusNoticeResponse publish(Long noticeId) {
        CampusNotice notice = locked(noticeId);
        if (notice.getStatus() == CampusNoticeStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_PUBLISH);
        }
        if (notice.publish(clock.instant())) {
            notices.saveAndFlush(notice);
            notifications.noticePublished(notice);
        }
        return AdminCampusNoticeResponse.of(notice);
    }

    /** Archives a draft or published notice for good. Archiving an archived notice changes nothing. */
    public AdminCampusNoticeResponse archive(Long noticeId) {
        CampusNotice notice = locked(noticeId);
        if (notice.archive()) {
            notices.saveAndFlush(notice);
        }
        return AdminCampusNoticeResponse.of(notice);
    }

    private CampusNotice locked(Long noticeId) {
        return (noticeId == null ? Optional.<CampusNotice>empty() : notices.findByIdForUpdate(noticeId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static ApiException invalid(IllegalArgumentException invalid) {
        return new ApiException(HttpStatus.BAD_REQUEST, "The notice is not valid: " + invalid.getMessage() + ".");
    }
}
