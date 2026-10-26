package com.stacc.backend.campus.notices;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.campus.CampusActor;
import com.stacc.backend.campus.notices.CampusNoticeResponses.AdminCampusNoticePage;
import com.stacc.backend.campus.notices.CampusNoticeResponses.AdminCampusNoticeResponse;
import com.stacc.backend.campus.notices.CampusNoticeResponses.CampusNoticePage;
import com.stacc.backend.campus.notices.CampusNoticeResponses.CampusNoticeResponse;
import com.stacc.backend.common.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading campus notices: the published feed for any role the account holds, and every notice for admins. */
@Service
@Transactional(readOnly = true)
public class CampusNoticeQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final CampusNoticeRepository notices;

    public CampusNoticeQueryService(CampusNoticeRepository notices) {
        this.notices = notices;
    }

    /**
     * One page of the published notices visible to any role the actor holds, newest first, each once. Drafts and
     * archived notices never appear. Empty for an account holding none of the three roles.
     */
    public CampusNoticePage feed(CampusActor actor, int page, int size) {
        PageRequest request = pageOf(page, size);
        boolean student = actor.holds("STUDENT");
        boolean faculty = actor.holds("FACULTY");
        boolean admin = actor.holds("ADMIN");
        if (!student && !faculty && !admin) {
            return new CampusNoticePage(List.of(), request.getPageNumber(), request.getPageSize(), 0, 0);
        }
        Page<CampusNotice> rows = notices.findFeed(student, faculty, admin, request);
        return new CampusNoticePage(rows.getContent().stream().map(CampusNoticeResponse::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public AdminCampusNoticePage adminNotices(CampusNoticeStatus status, int page, int size) {
        PageRequest request = pageOf(page, size);
        Page<CampusNotice> rows = notices.findAdminPage(status, request);
        return new AdminCampusNoticePage(rows.getContent().stream().map(AdminCampusNoticeResponse::of).toList(),
                request.getPageNumber(), request.getPageSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    public AdminCampusNoticeResponse adminNotice(Long noticeId) {
        return (noticeId == null ? Optional.<CampusNotice>empty() : notices.findById(noticeId))
                .map(AdminCampusNoticeResponse::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CampusNoticeService.NOT_FOUND));
    }

    private static PageRequest pageOf(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size);
    }
}
