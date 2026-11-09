package com.stacc.backend.search;

import java.util.Optional;
import java.util.Set;

import com.stacc.backend.hostel.HostelApplication;
import com.stacc.backend.hostel.HostelApplicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Hostel applications, as the hostel routes allow: the hostel office (ADMIN with HOSTEL_MANAGE) searches every
 * application by number, student login ID, or session and opens the admin review; students (STUDENT with
 * HOSTEL_APPLICATION_VIEW_OWN) search only their own and open them on their Hostel page. Faculty-only accounts get no
 * section. The approval status comes from the approval engine.
 */
@Component
public class HostelSearchProvider implements SearchProvider {

    private final HostelApplicationRepository applications;

    public HostelSearchProvider(HostelApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    public Set<SearchResultType> types() {
        return Set.of(SearchResultType.HOSTEL_APPLICATION);
    }

    @Override
    public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
        SearchActor actor = context.actor();
        SearchText text = context.text();
        if (type != SearchResultType.HOSTEL_APPLICATION) {
            return Optional.empty();
        }
        if (actor.holdsWith("ADMIN", "HOSTEL_MANAGE")) {
            return Optional.of(applications.searchAll(text.exact(), text.prefix(), text.contains(), page)
                    .map(application -> resultOf(application, true)));
        }
        if (actor.holdsWith("STUDENT", "HOSTEL_APPLICATION_VIEW_OWN")) {
            return Optional.of(applications.searchOwn(actor.accountId(), text.exact(), text.prefix(), text.contains(), page)
                    .map(application -> resultOf(application, false)));
        }
        return Optional.empty();
    }

    private static SearchResult resultOf(HostelApplication application, boolean office) {
        return new SearchResult(SearchResultType.HOSTEL_APPLICATION, application.getApplicationNumber(),
                SearchFormat.join("Hostel application", application.getAcademicSession().getCode(),
                        application.approvalStatus() == null ? null
                                : "Approval: " + SearchFormat.words(application.approvalStatus()),
                        office ? application.getStudentProfile().getUserAccount().getLoginId() : null),
                null, office ? "/admin/hostel/applications/" + application.getId() : "/hostel?application=" + application.getId(),
                application.getSubmittedAt());
    }
}
