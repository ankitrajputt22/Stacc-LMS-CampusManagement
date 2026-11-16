package com.stacc.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A copy is lent once at a time, and a held title goes to the queue first. A title of its own. */
class LibraryLendingMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private LibraryService library;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aCopyIsLentOnceAndHoldsComeFirst() throws Exception {
        Long librarian = accounts.findByLoginId("librarian1").map(account -> account.getId())
                .orElseGet(() -> accounts.findByLoginId("admin1").orElseThrow().getId());
        Long holder = accounts.findByLoginId("student2").orElseThrow().getId();
        Long titleId = library.addTitle("Race algorithms", "Test Author", null, null, 2020, "Testing").titleId();
        library.addCopy(titleId, "RACE-0001", "T1");

        List<Concurrently.Outcome<LibraryService.LoanView>> outcomes = Concurrently.run(
                () -> library.issue(librarian, "RACE-0001", "student1"),
                () -> library.issue(librarian, "RACE-0001", "faculty1"));
        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);

        library.placeHold(holder, titleId);
        String borrower = library.giveBack("RACE-0001");
        assertThat(borrower).isIn("student1", "faculty1");
        assertThatThrownBy(() -> library.issue(librarian, "RACE-0001", borrower)).isInstanceOf(ApiException.class)
                .hasMessageContaining("held for others");
        library.issue(librarian, "RACE-0001", "student2");
        assertThat(library.mine(holder).holds()).isEmpty();
        assertThat(library.mine(holder).loans()).singleElement().extracting(LibraryService.LoanView::barcode).isEqualTo("RACE-0001");
    }
}
