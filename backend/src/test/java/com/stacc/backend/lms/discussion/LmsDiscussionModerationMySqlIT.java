package com.stacc.backend.lms.discussion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two reports of the same post by the same student at once leave one open report; hiding resolves it, and students
 * then no longer see the thread; a locked thread takes no replies. Uses a thread of its own in CS301's published course.
 */
class LmsDiscussionModerationMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LmsDiscussionService discussions;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reportsHidingAndLockingHoldUnderTheThreadLock() throws Exception {
        Viewer faculty = new Viewer(accounts.findByLoginId("faculty1").orElseThrow().getId(), AuthorKind.FACULTY);
        Viewer student = new Viewer(accounts.findByLoginId("student1").orElseThrow().getId(), AuthorKind.STUDENT);
        Long lmsCourseId = jdbc.queryForObject("""
                select lms.id from lms_courses lms
                join course_offerings offering on offering.id = lms.course_offering_id
                join courses course on course.id = offering.course_id
                where course.code = 'CS301' and lms.status = 'PUBLISHED'
                """, Long.class);
        Long threadId = discussions.start(faculty, lmsCourseId, "Moderation test", "A post to report.").thread().getId();

        List<Concurrently.Outcome<Object>> outcomes = Concurrently.run(
                () -> discussions.report(student, lmsCourseId, threadId, null, "Off topic"),
                () -> discussions.report(student, lmsCourseId, threadId, null, "Off topic"));
        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));
        assertThat(discussions.openReports(faculty, lmsCourseId)).filteredOn(report -> report.getThread().getId().equals(threadId)).hasSize(1);

        discussions.lockThread(faculty, lmsCourseId, threadId, true);
        assertThatThrownBy(() -> discussions.reply(student, lmsCourseId, threadId, "Still here"))
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        discussions.hideThread(faculty, lmsCourseId, threadId, true);
        assertThat(discussions.openReports(faculty, lmsCourseId)).noneMatch(report -> report.getThread().getId().equals(threadId));
        assertThat(discussions.list(student, lmsCourseId)).noneMatch(summary -> summary.thread().getId().equals(threadId));
        assertThatThrownBy(() -> discussions.detail(student, lmsCourseId, threadId))
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(jdbc.queryForObject("select resolution from lms_discussion_reports where thread_id = ?", String.class, threadId))
                .isEqualTo("HIDDEN");
    }
}
