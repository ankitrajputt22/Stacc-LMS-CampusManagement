package com.stacc.backend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.mentoring.MentoringActionItem;
import com.stacc.backend.mentoring.MentoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PortfolioMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private PortfolioService portfolios;

    @Autowired
    private MentoringService mentoring;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void anItemIsVerifiedOnlyByTheNamedFacultyMember() {
        Long student = accounts.findByLoginId("cse24002").orElseThrow().getId();
        Long named = accounts.findByLoginId("emp1022").orElseThrow().getId();
        Long other = accounts.findByLoginId("emp1021").orElseThrow().getId();
        Long itemId = portfolios.add(student, new PortfolioService.ItemInput(PortfolioItem.Kind.ACHIEVEMENT, "Hackathon finalist", null,
                LocalDate.of(2026, 9, 1), null)).itemId();
        portfolios.add(student, new PortfolioService.ItemInput(PortfolioItem.Kind.SKILL, "Undated skill", null, null, null));
        portfolios.requestVerification(student, itemId, "emp1022");

        assertThatThrownBy(() -> portfolios.decide(other, itemId, true, null)).isInstanceOf(ApiException.class);
        assertThat(portfolios.waitingFor(named)).extracting(PortfolioService.WaitingItem::itemId).contains(itemId);
        portfolios.decide(named, itemId, true, "Saw the certificate.");

        PortfolioService.Portfolio mine = portfolios.mine(student);
        assertThat(mine.items()).filteredOn(item -> item.itemId().equals(itemId))
                .singleElement().extracting(PortfolioService.ItemView::verification).isEqualTo(PortfolioItem.Verification.VERIFIED);
        assertThat(mine.items().getLast().occurredOn()).isNull();
    }

    @Test
    void mentoringListsRunOnMySql() {
        Long mentor = accounts.findByLoginId("emp1022").orElseThrow().getId();
        Long student = accounts.findByLoginId("cse24002").orElseThrow().getId();
        Long pairingId = mentoring.pairings(mentor).stream().filter(pairing -> pairing.menteeLoginId().equals("cse24002")).findFirst()
                .map(MentoringService.PairingView::assignmentId)
                .orElseGet(() -> mentoring.pair("emp1022", "cse24002").assignmentId());
        Long meetingId = mentoring.addMeeting(mentor, pairingId, Instant.now().truncatedTo(ChronoUnit.SECONDS), "IT meeting", "Shared", "Private")
                .meetings().getFirst().meetingId();
        mentoring.addActionItem(mentor, meetingId, "Undated task", MentoringActionItem.Owner.MENTEE, null);
        mentoring.addActionItem(mentor, meetingId, "Dated task", MentoringActionItem.Owner.MENTEE, Instant.now().plus(3, ChronoUnit.DAYS));

        List<MentoringService.OpenItem> open = mentoring.openItems(student);
        assertThat(open).extracting(MentoringService.OpenItem::description).containsSubsequence("Dated task", "Undated task");
        assertThat(mentoring.allPairings()).isNotEmpty();
    }
}
