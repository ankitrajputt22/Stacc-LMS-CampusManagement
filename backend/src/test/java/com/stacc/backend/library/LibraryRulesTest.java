package com.stacc.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import org.junit.jupiter.api.Test;

/** Catalog entries are checked, a loan renews once before it is due, and overdue is worked out from the due time. */
class LibraryRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void catalogEntriesAreChecked() {
        assertThatThrownBy(() -> new LibraryTitle(" ", null, null, null, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibraryTitle("Algorithms", null, "12345", null, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new LibraryTitle("Algorithms", null, "978-0-262-04630-5", null, 2022, null, NOW).getIsbn()).isEqualTo("9780262046305");
        assertThat(new LibraryCopy(mock(LibraryTitle.class), " lib-0001 ", "A3", NOW).getBarcode()).isEqualTo("LIB-0001");
        assertThatThrownBy(() -> new LibraryResource("Journal", null, "http://example.org", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLoanRenewsOnceBeforeItIsDue() {
        LibraryLoan loan = new LibraryLoan(mock(LibraryCopy.class), mock(UserAccount.class), 1L, NOW);
        assertThat(loan.getDueAt()).isEqualTo(NOW.plus(LibraryLoan.PERIOD));
        assertThat(loan.overdue(NOW.plus(Duration.ofDays(15)))).isTrue();
        assertThatThrownBy(() -> loan.renew(NOW.plus(Duration.ofDays(15)))).isInstanceOf(IllegalStateException.class);
        loan.renew(NOW.plus(Duration.ofDays(10)));
        assertThat(loan.getDueAt()).isEqualTo(NOW.plus(LibraryLoan.PERIOD).plus(LibraryLoan.PERIOD));
        assertThatThrownBy(() -> loan.renew(NOW.plus(Duration.ofDays(11)))).isInstanceOf(IllegalStateException.class);
        assertThat(loan.giveBack(NOW)).isTrue();
        assertThat(loan.giveBack(NOW)).isFalse();
        assertThat(loan.overdue(NOW.plus(Duration.ofDays(60)))).isFalse();
    }
}
