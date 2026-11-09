package com.stacc.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

class GlobalSearchServiceTest {

    private final SearchActor student = new SearchActor(70L, Set.of("STUDENT"), Set.of("GLOBAL_SEARCH_USE"));

    /** A provider with fixed totals per kind, which records the page requests it gets. */
    private static final class FakeProvider implements SearchProvider {
        private final Set<SearchResultType> types;
        private final long total;
        private final boolean allowed;
        final List<Pageable> asked = new ArrayList<>();

        FakeProvider(long total, boolean allowed, SearchResultType... types) {
            this.types = Set.of(types);
            this.total = total;
            this.allowed = allowed;
        }

        @Override
        public Set<SearchResultType> types() {
            return types;
        }

        @Override
        public Optional<Page<SearchResult>> search(SearchResultType type, SearchContext context, Pageable page) {
            asked.add(page);
            if (!allowed) {
                return Optional.empty();
            }
            List<SearchResult> items = new ArrayList<>();
            for (long i = page.getOffset(); i < Math.min(total, page.getOffset() + page.getPageSize()); i++) {
                items.add(new SearchResult(type, type.label() + " " + i, null, null, "/x/" + i, null));
            }
            return Optional.of(new PageImpl<>(items, page, total));
        }
    }

    @Test
    void thePreviewKeepsKindsWithResultsInTheFixedOrderWithFiveEachAndTheirTotals() {
        FakeProvider notices = new FakeProvider(2, true, SearchResultType.CAMPUS_NOTICE);
        FakeProvider courses = new FakeProvider(12, true, SearchResultType.COURSE);
        FakeProvider empty = new FakeProvider(0, true, SearchResultType.ASSIGNMENT);
        FakeProvider denied = new FakeProvider(9, false, SearchResultType.HELPDESK_TICKET);
        GlobalSearchService service = new GlobalSearchService(List.of(notices, courses, empty, denied));

        var response = service.preview(student, "  data   structures ");

        assertThat(response.query()).isEqualTo("data structures");
        assertThat(response.sections()).extracting(section -> section.type())
                .containsExactly(SearchResultType.COURSE, SearchResultType.CAMPUS_NOTICE);
        assertThat(response.sections().get(0).items()).hasSize(GlobalSearchService.PREVIEW_LIMIT);
        assertThat(response.sections().get(0).totalCount()).isEqualTo(12);
        assertThat(response.sections().get(0).label()).isEqualTo("Courses");
        assertThat(courses.asked).singleElement().satisfies(page -> {
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(5);
        });
    }

    @Test
    void oneKindIsPagedUnderTheSameRules() {
        FakeProvider courses = new FakeProvider(45, true, SearchResultType.COURSE);
        GlobalSearchService service = new GlobalSearchService(List.of(courses));

        var page = service.results(student, "data", SearchResultType.COURSE, 2, 20);

        assertThat(page.items()).hasSize(5);
        assertThat(page.totalElements()).isEqualTo(45);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.label()).isEqualTo("Courses");
    }

    @Test
    void aKindTheActorMayNotSearchIsAnEmptyPageNeverARefusal() {
        GlobalSearchService service = new GlobalSearchService(List.of(new FakeProvider(9, false,
                SearchResultType.HELPDESK_TICKET)));

        var denied = service.results(student, "data", SearchResultType.HELPDESK_TICKET, 0, 20);
        var unanswered = service.results(student, "data", SearchResultType.DEPARTMENT, 0, 20);

        assertThat(denied.items()).isEmpty();
        assertThat(denied.totalElements()).isZero();
        assertThat(unanswered.items()).isEmpty();
    }

    @Test
    void invalidTextTypePageAndSizeAre400() {
        GlobalSearchService service = new GlobalSearchService(List.of());
        List<Runnable> calls = List.of(
                () -> service.preview(student, "a"),
                () -> service.results(student, " ", SearchResultType.COURSE, 0, 20),
                () -> service.results(student, "data", null, 0, 20),
                () -> service.results(student, "data", SearchResultType.COURSE, -1, 20),
                () -> service.results(student, "data", SearchResultType.COURSE, 0, 0),
                () -> service.results(student, "data", SearchResultType.COURSE, 0, 101));
        for (Runnable call : calls) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                    refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void eachKindHasOneProvider() {
        assertThatThrownBy(() -> new GlobalSearchService(List.of(new FakeProvider(1, true, SearchResultType.COURSE),
                new FakeProvider(1, true, SearchResultType.COURSE)))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRealProvidersAnswerEveryKindOnce() {
        // The six providers together cover every result kind; the constructor refuses overlaps.
        List<SearchResultType> covered = new ArrayList<>();
        for (SearchProvider provider : List.<SearchProvider>of(
                new LmsSearchProvider(null, null, null, null, null, null), new CalendarSearchProvider(null, null),
                new CampusSearchProvider(null, null), new HelpdeskSearchProvider(null, null),
                new HostelSearchProvider(null), new AcademicSearchProvider(null, null, null, null, null, null))) {
            covered.addAll(provider.types());
        }
        assertThat(covered).containsExactlyInAnyOrder(SearchResultType.values());
    }
}
