package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.campus.notices.CampusNotice;
import com.stacc.backend.campus.notices.CampusNoticeRepository;
import com.stacc.backend.campus.services.CampusServiceRequest;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Campus notices and service requests. Notices follow the notice feed: published only, visible to any role the account
 * holds, each once, with CAMPUS_NOTICE_VIEW. Requests follow their routes: the campus office (ADMIN with
 * CAMPUS_SERVICE_MANAGE) searches every request and opens the admin review; requesters (STUDENT or FACULTY with
 * CAMPUS_SERVICE_REQUEST_VIEW_OWN) search only their own and open their request page. Statuses shown are the effective
 * ones, with the approval's outcome taken into account.
 */
@Component
public class CampusSearchProvider implements SearchProvider {

    private final CampusNoticeRepository notices;
    private final CampusServiceRequestRepository requests;

    public CampusSearchProvider(CampusNoticeRepository notices, CampusServiceRequestRepository requests) {
        this.notices = notices;
        this.requests = requests;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.CAMPUS_NOTICE, SearchResultType.CAMPUS_SERVICE_REQUEST);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        SearchText text = context.text();
        if (type == SearchResultType.CAMPUS_NOTICE) {
            if (!actor.can("CAMPUS_NOTICE_VIEW") || !actor.holdsAnyCollegeRole()) {
                return Optional.empty();
            }
            return Optional.of(notices.searchFeed(actor.holds("STUDENT"), actor.holds("FACULTY"), actor.holds("ADMIN"),
                    text.exact(), text.prefix(), text.contains(), page).map(CampusSearchProvider::noticeOf));
        }
        if (type == SearchResultType.CAMPUS_SERVICE_REQUEST) {
            if (actor.holdsWith("ADMIN", "CAMPUS_SERVICE_MANAGE")) {
                return Optional.of(requests.searchAll(text.exact(), text.prefix(), text.contains(), page)
                        .map(request -> requestOf(request, true)));
            }
            if ((actor.holds("STUDENT") || actor.holds("FACULTY")) && actor.can("CAMPUS_SERVICE_REQUEST_VIEW_OWN")) {
                return Optional.of(requests.searchOwn(actor.accountId(), text.exact(), text.prefix(), text.contains(), page)
                        .map(request -> requestOf(request, false)));
            }
        }
        return Optional.empty();
    }

    private static SearchResult noticeOf(CampusNotice notice) {
        return new SearchResult(SearchResultType.CAMPUS_NOTICE, notice.getTitle(), "Campus notice",
                SearchFormat.snippet(notice.getMessage()), "/campus", notice.getPublishedAt());
    }

    private static SearchResult requestOf(CampusServiceRequest request, boolean office) {
        return new SearchResult(SearchResultType.CAMPUS_SERVICE_REQUEST, request.getSubject(),
                SearchFormat.join(request.getRequestNumber(), request.getServiceDefinition().getName(),
                        SearchFormat.words(request.effectiveStatus()), office ? request.getRequester().getLoginId() : null),
                null, (office ? "/admin/campus/requests/" : "/campus/requests/") + request.getId(),
                request.getSubmittedAt());
    }
}
