package com.stacc.backend.campus.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.stacc.backend.campus.CampusFixtures;
import org.junit.jupiter.api.Test;

class CampusNoticeTest {

    @Test
    void aNewNoticeIsADraftVisibleToAtLeastOneRole() {
        CampusNotice notice = CampusFixtures.notice(5, true, false, false);

        assertThat(notice.getStatus()).isEqualTo(CampusNoticeStatus.DRAFT);
        assertThat(notice.getPublishedAt()).isNull();
        assertThatThrownBy(() -> CampusFixtures.notice(6, false, false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusNotice(new CampusNotice.Content(" ", "M", true, false, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusNotice(new CampusNotice.Content("T", "x".repeat(10_001), true, false, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampusNotice(new CampusNotice.Content("x".repeat(201), "M", true, false, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishingSetsThePublicationTimeOnceAndRepeatsChangeNothing() {
        CampusNotice notice = CampusFixtures.notice(5, true, false, false);

        assertThat(notice.publish(CampusFixtures.NOW)).isTrue();
        assertThat(notice.getStatus()).isEqualTo(CampusNoticeStatus.PUBLISHED);
        assertThat(notice.getPublishedAt()).isEqualTo(CampusFixtures.NOW.truncatedTo(ChronoUnit.MICROS));

        assertThat(notice.publish(CampusFixtures.NOW.plusSeconds(60))).isFalse();
        notice.edit(new CampusNotice.Content("Corrected", "Message", true, true, false));
        assertThat(notice.getPublishedAt()).isEqualTo(CampusFixtures.NOW.truncatedTo(ChronoUnit.MICROS));
        assertThat(notice.getStatus()).isEqualTo(CampusNoticeStatus.PUBLISHED);
        assertThat(notice.getTitle()).isEqualTo("Corrected");
    }

    @Test
    void archivingIsFinalAndKeepsThePublicationTime() {
        CampusNotice notice = CampusFixtures.notice(5, true, false, false);
        notice.publish(CampusFixtures.NOW);

        assertThat(notice.archive()).isTrue();
        assertThat(notice.archive()).isFalse();
        assertThat(notice.getStatus()).isEqualTo(CampusNoticeStatus.ARCHIVED);
        assertThat(notice.getPublishedAt()).isNotNull();
        assertThatThrownBy(() -> notice.publish(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notice.edit(new CampusNotice.Content("T", "M", true, false, false)))
                .isInstanceOf(IllegalStateException.class);

        CampusNotice draft = CampusFixtures.notice(6, false, true, false);
        assertThat(draft.archive()).isTrue();
        assertThat(draft.getPublishedAt()).isNull();
    }

    @Test
    void visibilityMatchesAnyRoleTheAccountHolds() {
        CampusNotice studentOnly = CampusFixtures.notice(5, true, false, false);

        assertThat(studentOnly.isVisibleTo(Set.of("STUDENT"))).isTrue();
        assertThat(studentOnly.isVisibleTo(Set.of("FACULTY"))).isFalse();
        assertThat(studentOnly.isVisibleTo(Set.of("ADMIN"))).isFalse();
        assertThat(studentOnly.isVisibleTo(Set.of("ADMIN", "STUDENT"))).isTrue();
        assertThat(CampusFixtures.notice(6, false, false, true).isVisibleTo(Set.of("ADMIN"))).isTrue();
    }

    @Test
    void thereIsNoDeleteRestoreOrSchedule() {
        assertThat(CampusNotice.class.getMethods()).noneMatch(method -> method.getName().matches(
                "(?i).*(delete|restore|unarchive|schedule|unpublish).*"));
        assertThat(CampusNoticeService.class.getMethods()).noneMatch(method -> method.getName().matches(
                "(?i).*(delete|restore|unarchive|schedule|unpublish).*"));
    }
}
