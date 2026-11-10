package com.stacc.backend.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** Links are https only; an item is verified once, and a verified or waiting item is no longer edited. */
class PortfolioItemTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private PortfolioItem item() {
        return new PortfolioItem(mock(UserAccount.class), PortfolioItem.Kind.PROJECT, "Campus app", null, LocalDate.of(2026, 10, 1),
                "https://example.org/app", NOW);
    }

    @Test
    void linksAreOneHttpsAddress() {
        assertThatThrownBy(() -> new PortfolioItem(mock(UserAccount.class), PortfolioItem.Kind.PROJECT, "App", null, null,
                "javascript:alert(1)", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortfolioItem(mock(UserAccount.class), PortfolioItem.Kind.PROJECT, "App", null, null,
                "http://example.org", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item().getLink()).isEqualTo("https://example.org/app");
    }

    @Test
    void verificationIsDecidedOnceAndLocksTheItem() {
        PortfolioItem item = item();
        item.requestVerification(mock(UserAccount.class), NOW);
        assertThatThrownBy(() -> item.edit(PortfolioItem.Kind.PROJECT, "Changed", null, null, null)).isInstanceOf(IllegalStateException.class);
        item.decide(true, "Seen the demo.", NOW);
        assertThat(item.getVerification()).isEqualTo(PortfolioItem.Verification.VERIFIED);
        assertThatThrownBy(() -> item.decide(false, null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> item.edit(PortfolioItem.Kind.PROJECT, "Changed", null, null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDeclinedItemStartsOverWhenEdited() {
        PortfolioItem item = item();
        item.requestVerification(mock(UserAccount.class), NOW);
        item.decide(false, "No evidence.", NOW);
        item.edit(PortfolioItem.Kind.PROJECT, "Campus app v2", null, null, null);
        assertThat(item.getVerification()).isEqualTo(PortfolioItem.Verification.NONE);
        assertThat(item.getVerifier()).isNull();
    }
}
