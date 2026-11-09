package com.stacc.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static Notification withPath(String actionPath) {
        return new Notification(NotificationType.RESULT_PUBLISHED, "Result published", "Your result was published.",
                NotificationSourceType.COURSE_RESULT, 9L, actionPath);
    }

    @Test
    void onlyPathsInsideTheApplicationAreAccepted() {
        for (String safe : new String[] {"/calendar", "/courses/12/assignments/44", "/academic/results", "/inbox?x=1"}) {
            assertThat(Notification.isSafeActionPath(safe)).as(safe).isTrue();
            assertThat(withPath(safe).getActionPath()).isEqualTo(safe);
        }
        for (String unsafe : new String[] {"https://example.com", "http://localhost/calendar", "//evil.example",
                "/\\\\evil.example", "calendar", "/a/../admin", "/path with space", "javascript:alert(1)", "", "/x\\ny"}) {
            assertThat(Notification.isSafeActionPath(unsafe)).as(unsafe).isFalse();
            assertThatThrownBy(() -> withPath(unsafe)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(withPath(null).getActionPath()).isNull();
    }

    @Test
    void titleAndMessageArePlainTextWithinTheirLimits() {
        assertThatThrownBy(() -> new Notification(NotificationType.RESULT_PUBLISHED, " ", "Message", null, null, null))
                .hasMessageContaining("title");
        assertThatThrownBy(() -> new Notification(NotificationType.RESULT_PUBLISHED, "Title", "x".repeat(2001), null, null,
                null)).hasMessageContaining("at most 2000");
        assertThatThrownBy(() -> new Notification(null, "Title", "Message", null, null, null)).hasMessageContaining("type");
    }

    @Test
    void aSourceIsATypeAndAnIdTogether() {
        assertThatThrownBy(() -> new Notification(NotificationType.RESULT_PUBLISHED, "Title", "Message",
                NotificationSourceType.COURSE_RESULT, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification(NotificationType.RESULT_PUBLISHED, "Title", "Message", null, 4L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readStateIsTheReadTimeAndBothDirectionsAreIdempotent() {
        NotificationRecipient recipient = new NotificationRecipient(withPath("/calendar"), new UserAccount("STU-1", "hash"));
        assertThat(recipient.isRead()).isFalse();
        assertThat(recipient.getReadAt()).isNull();

        recipient.markRead(Instant.parse("2026-10-21T08:00:00.123456789Z"));
        recipient.markRead(Instant.parse("2026-10-22T08:00:00Z"));
        assertThat(recipient.getReadAt()).isEqualTo(Instant.parse("2026-10-21T08:00:00.123456Z"));

        recipient.markUnread();
        recipient.markUnread();
        assertThat(recipient.isRead()).isFalse();
    }
}
