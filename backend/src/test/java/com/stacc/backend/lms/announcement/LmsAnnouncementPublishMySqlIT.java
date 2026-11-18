package com.stacc.backend.lms.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class LmsAnnouncementPublishMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsAnnouncementManagementService announcements;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoPublicationsAtOnceNotifyOnce() throws Exception {
        Long faculty = accounts.findByLoginId("emp1021").orElseThrow().getId();
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long announcementId = announcements.create(faculty, lmsCourseId, "Publish race", "Posted once.").getId();

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> announcements.publish(faculty, announcementId),
                () -> announcements.publish(faculty, announcementId));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from notifications where source_type = 'ANNOUNCEMENT' and source_id = ?",
                Integer.class, announcementId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from notification_recipients recipient join notifications notification
                on notification.id = recipient.notification_id
                where notification.source_type = 'ANNOUNCEMENT' and notification.source_id = ?
                """, Integer.class, announcementId)).isPositive();
    }
}
