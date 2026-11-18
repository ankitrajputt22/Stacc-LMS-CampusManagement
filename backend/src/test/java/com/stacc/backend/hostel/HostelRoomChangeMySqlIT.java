package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HostelRoomChangeMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private HostelRoomChangeService changes;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoMoveRequestsAtOnceOpenOneChange() throws Exception {
        Long student = accounts.findByLoginId("cse24002").orElseThrow().getId();

        List<Concurrently.Outcome<HostelRoomChangeService.RoomChangeView>> outcomes = Concurrently.run(
                () -> changes.requestMove(student, "Too noisy at night", "A quiet floor"),
                () -> changes.requestMove(student, "Too noisy at night", "A quiet floor"));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));
        HostelRoomChangeService.RoomChangeView opened = outcomes.stream().filter(Concurrently.Outcome::succeeded)
                .findFirst().orElseThrow().result();
        assertThat(opened.approvalStatus()).isEqualTo("PENDING");

        changes.cancel(student, opened.changeId());
        assertThat(jdbc.queryForObject("select status from hostel_room_changes where id = ?", String.class, opened.changeId()))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("""
                select request.status from approval_requests request join hostel_room_changes room_change
                on room_change.approval_request_id = request.id where room_change.id = ?
                """, String.class, opened.changeId())).isEqualTo("CANCELLED");
    }
}
