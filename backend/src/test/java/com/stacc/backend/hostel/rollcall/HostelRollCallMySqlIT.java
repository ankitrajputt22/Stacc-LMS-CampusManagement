package com.stacc.backend.hostel.rollcall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two wardens' requests to open the same night's sheet at once make one sheet (the unique sheet key); marking every
 * resident lets it be finalized. Uses the Main Hostel and warden1 on a night far in the future that no other test uses.
 */
class HostelRollCallMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private HostelRollCallService rollCalls;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void oneSheetPerNightAndEveryResidentMarkedBeforeFinalizing() throws Exception {
        Long warden = accounts.findByLoginId("warden1").orElseThrow().getId();
        Long hostelId = jdbc.queryForObject("select id from hostels where code = 'MAIN'", Long.class);
        LocalDate night = LocalDate.of(2031, 3, 14);

        List<Concurrently.Outcome<HostelRollCallService.Detail>> outcomes = Concurrently.run(
                () -> rollCalls.open(warden, hostelId, null, night),
                () -> rollCalls.open(warden, hostelId, null, night));
        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome ->
                assertThat(outcome.failure()).isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getStatus()).isEqualTo(HttpStatus.CONFLICT)));
        HostelRollCallService.Detail sheet = outcomes.stream().filter(Concurrently.Outcome::succeeded).findFirst().orElseThrow().result();
        assertThat(sheet.entries()).noneMatch(entry -> entry.status() == HostelRollCallEntry.Status.ABSENT);

        List<HostelRollCallService.Mark> marks = sheet.entries().stream()
                .map(entry -> new HostelRollCallService.Mark(entry.entryId(), HostelRollCallEntry.Status.PRESENT)).toList();
        if (!marks.isEmpty()) {
            rollCalls.mark(warden, sheet.summary().rollCallId(), marks);
        }
        HostelRollCallService.Detail done = rollCalls.finalizeRollCall(warden, sheet.summary().rollCallId());
        assertThat(done.summary().status()).isEqualTo(HostelRollCall.Status.FINALIZED);
        assertThat(done.summary().unmarked()).isZero();
    }
}
