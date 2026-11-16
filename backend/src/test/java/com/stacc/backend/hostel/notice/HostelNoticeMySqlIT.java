package com.stacc.backend.hostel.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Publishing the same emergency notice twice at once publishes it once and tells the residents once. Main Hostel, warden1. */
class HostelNoticeMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private HostelNoticeService notices;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anEmergencyIsAnnouncedOnce() throws Exception {
        HostelNoticeService.Manager warden = new HostelNoticeService.Manager(accounts.findByLoginId("warden1").orElseThrow().getId(), false);
        Long hostelId = jdbc.queryForObject("select id from hostels where code = 'MAIN'", Long.class);
        Long noticeId = notices.add(warden, hostelId, null, "Fire drill", "Leave by the nearest stairs at 6 pm.", true).noticeId();

        List<Concurrently.Outcome<HostelNoticeService.NoticeView>> outcomes = Concurrently.run(
                () -> notices.publish(warden, noticeId),
                () -> notices.publish(warden, noticeId));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_type = 'HOSTEL_NOTICE' and source_id = ?",
                Integer.class, noticeId)).isLessThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select status from hostel_notices where id = ?", String.class, noticeId)).isEqualTo("PUBLISHED");
    }
}
