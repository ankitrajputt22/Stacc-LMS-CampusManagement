package com.stacc.backend.campus.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.campus.CampusFixtures;
import com.stacc.backend.campus.CampusNotifications;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CampusNoticeServiceTest {

    private final CampusNoticeRepository notices = mock(CampusNoticeRepository.class);
    private final CampusNotifications notifications = mock(CampusNotifications.class);
    private final CampusNoticeService service = new CampusNoticeService(notices, notifications,
            Clock.fixed(CampusFixtures.NOW, ZoneOffset.UTC));

    private final CampusNotice notice = CampusFixtures.notice(5, true, false, false);

    @BeforeEach
    void setUp() {
        when(notices.saveAndFlush(any())).thenAnswer(call -> {
            CampusNotice saved = call.getArgument(0);
            return saved.getId() == null ? CampusFixtures.withId(saved, 9) : saved;
        });
        when(notices.findByIdForUpdate(5L)).thenReturn(Optional.of(notice));
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(refusal.getMessage()).isEqualTo(message);
            }
        });
    }

    private static CampusNoticeInput input(boolean student, boolean faculty, boolean admin) {
        return new CampusNoticeInput("Library closed", "The library is closed on Friday.", student, faculty, admin);
    }

    @Test
    void aNewNoticeIsADraftAndNotifiesNobody() {
        var created = service.create(input(true, true, false));

        assertThat(created.noticeId()).isEqualTo(9L);
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.publishedAt()).isNull();
        assertThat(CampusNoticeInput.class.getRecordComponents()).noneMatch(part ->
                part.getName().equals("status") || part.getName().equals("publishedAt"));
        verify(notifications, never()).noticePublished(any());
    }

    @Test
    void aNoticeWithoutAnyRoleIs400() {
        assertRefused(() -> service.create(input(false, false, false)), HttpStatus.BAD_REQUEST, null);
        assertRefused(() -> service.update(5L, input(false, false, false)), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    void theFirstPublicationNotifiesOnceAndRepeatsAreStable() {
        var first = service.publish(5L);
        var again = service.publish(5L);

        assertThat(first.status()).isEqualTo("PUBLISHED");
        assertThat(again.publishedAt()).isEqualTo(first.publishedAt());
        verify(notifications, times(1)).noticePublished(notice);
        verify(notices, times(2)).findByIdForUpdate(5L);
    }

    @Test
    void editingADraftOrPublishedNoticeNotifiesNobody() {
        service.update(5L, input(true, true, true));
        service.publish(5L);
        var edited = service.update(5L, input(false, true, false));

        assertThat(edited.status()).isEqualTo("PUBLISHED");
        assertThat(edited.visibleToFaculty()).isTrue();
        assertThat(edited.visibleToStudent()).isFalse();
        verify(notifications, times(1)).noticePublished(any());
    }

    @Test
    void anArchivedNoticeCannotBePublishedOrEditedAndArchivingAgainChangesNothing() {
        service.archive(5L);
        var again = service.archive(5L);

        assertThat(again.status()).isEqualTo("ARCHIVED");
        assertRefused(() -> service.publish(5L), HttpStatus.CONFLICT, CampusNoticeService.ARCHIVED_PUBLISH);
        assertRefused(() -> service.update(5L, input(true, false, false)), HttpStatus.CONFLICT,
                CampusNoticeService.ARCHIVED_EDIT);
        verify(notifications, never()).noticePublished(any());
    }

    @Test
    void anUnknownNoticeIs404() {
        assertRefused(() -> service.publish(99L), HttpStatus.NOT_FOUND, CampusNoticeService.NOT_FOUND);
        assertRefused(() -> service.archive(null), HttpStatus.NOT_FOUND, CampusNoticeService.NOT_FOUND);
    }
}
