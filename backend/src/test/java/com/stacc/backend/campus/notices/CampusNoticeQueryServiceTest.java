package com.stacc.backend.campus.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.campus.CampusFixtures;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class CampusNoticeQueryServiceTest {

    private final CampusNoticeRepository notices = mock(CampusNoticeRepository.class);
    private final CampusNoticeQueryService service = new CampusNoticeQueryService(notices);

    @Test
    void theFeedAsksForEveryRoleTheAccountHoldsInOneQuery() {
        CampusNotice notice = CampusFixtures.notice(5, true, true, false);
        notice.publish(CampusFixtures.NOW);
        when(notices.findFeed(true, true, false, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notice), PageRequest.of(0, 20), 1));

        var page = service.feed(CampusFixtures.actor(90, "ROLE_STUDENT", "ROLE_FACULTY"), 0, 20);

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Notice 5");
            assertThat(item.publishedAt()).isNotNull();
        });
        verify(notices).findFeed(true, true, false, PageRequest.of(0, 20));
    }

    @Test
    void eachRoleAsksOnlyForItsOwnFlag() {
        when(notices.findFeed(anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        service.feed(CampusFixtures.actor(70, "ROLE_STUDENT"), 0, 20);
        verify(notices).findFeed(true, false, false, PageRequest.of(0, 20));
        service.feed(CampusFixtures.actor(80, "ROLE_FACULTY"), 0, 20);
        verify(notices).findFeed(false, true, false, PageRequest.of(0, 20));
        service.feed(CampusFixtures.actor(1, "ROLE_ADMIN"), 0, 20);
        verify(notices).findFeed(false, false, true, PageRequest.of(0, 20));
    }

    @Test
    void anAccountWithNoMatchingRoleGetsAnEmptyFeed() {
        assertThat(service.feed(CampusFixtures.actor(5, "CAMPUS_NOTICE_VIEW"), 0, 20).items()).isEmpty();
        verify(notices, never()).findFeed(anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void theFeedShowsNoVisibilityOrStatus() {
        assertThat(CampusNoticeResponses.CampusNoticeResponse.class.getRecordComponents())
                .extracting(part -> part.getName()).containsExactly("noticeId", "title", "message", "publishedAt");
    }

    @Test
    void adminsReadEveryNoticeAndPagesAreBounded() {
        when(notices.findAdminPage(CampusNoticeStatus.DRAFT, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(CampusFixtures.notice(5, true, false, false))));
        when(notices.findById(5L)).thenReturn(Optional.of(CampusFixtures.notice(5, true, false, false)));

        assertThat(service.adminNotices(CampusNoticeStatus.DRAFT, 0, 20).items()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo("DRAFT"));
        assertThat(service.adminNotice(5L).visibleToStudent()).isTrue();
        assertThatThrownBy(() -> service.adminNotice(6L)).isInstanceOfSatisfying(ApiException.class,
                refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.feed(CampusFixtures.actor(70, "ROLE_STUDENT"), 0, 101))
                .isInstanceOf(ApiException.class);
    }
}
