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

/**
 * Runs the portfolio's live record queries and the verification flow on MySQL, and the mentoring lists that feed the
 * To-Do list. Works on student2, faculty2, and items and pairings of its own.
 */
class PortfolioMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private PortfolioService portfolios;

    @Autowired
    private MentoringService mentoring;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void anItemIsVerifiedOnlyByTheNamedFacultyMember() {
        Long student = accounts.findByLoginId("student2").orElseThrow().getId();
        Long named = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long other = accounts.findByLoginId("faculty1").orElseThrow().getId();
        Long itemId = portfolios.add(student, new PortfolioService.ItemInput(PortfolioItem.Kind.ACHIEVEMENT, "Hackathon finalist", null,
                LocalDate.of(2026, 9, 1), null)).itemId();
        portfolios.add(student, new PortfolioService.ItemInput(PortfolioItem.Kind.SKILL, "Undated skill", null, null, null));
        portfolios.requestVerification(student, itemId, "faculty2");

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
        Long mentor = accounts.findByLoginId("faculty2").orElseThrow().getId();
        Long student = accounts.findByLoginId("student2").orElseThrow().getId();
        Long pairingId = mentoring.pairings(mentor).stream().filter(pairing -> pairing.menteeLoginId().equals("student2")).findFirst()
                .map(MentoringService.PairingView::assignmentId)
                .orElseGet(() -> mentoring.pair("faculty2", "student2").assignmentId());
        Long meetingId = mentoring.addMeeting(mentor, pairingId, Instant.now().truncatedTo(ChronoUnit.SECONDS), "IT meeting", "Shared", "Private")
                .meetings().getFirst().meetingId();
        mentoring.addActionItem(mentor, meetingId, "Undated task", MentoringActionItem.Owner.MENTEE, null);
        mentoring.addActionItem(mentor, meetingId, "Dated task", MentoringActionItem.Owner.MENTEE, Instant.now().plus(3, ChronoUnit.DAYS));

        List<MentoringService.OpenItem> open = mentoring.openItems(student);
        assertThat(open).extracting(MentoringService.OpenItem::description).containsSubsequence("Dated task", "Undated task");
        assertThat(mentoring.allPairings()).isNotEmpty();
    }
}
