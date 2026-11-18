package com.stacc.backend.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeeRefundMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private FeeService fees;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aRefundIsNeverApprovedBeyondTheCredit() throws Exception {
        Long office = accounts.findByLoginId("adm1001").orElseThrow().getId();
        Long student = accounts.findByLoginId("ece25014").orElseThrow().getId();
        FeeService.Ledger before = fees.mine(student);
        BigDecimal over = before.due().max(BigDecimal.ZERO).add(new BigDecimal("1000.00"));
        fees.recordPayment(office, "ece25014", over, FeePayment.Mode.UPI, "RACE-1", LocalDate.now().minusDays(1));
        BigDecimal credit = fees.mine(student).credit();
        assertThat(credit).isEqualByComparingTo("1000.00");
        Long first = fees.askForRefund(student, credit, "Paid twice by mistake").adjustmentId();
        Long second = fees.askForRefund(student, credit, "Paid twice by mistake").adjustmentId();

        List<Concurrently.Outcome<FeeService.AdjustmentView>> outcomes = Concurrently.run(
                () -> fees.decide(office, first, true, null),
                () -> fees.decide(office, second, true, null));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(fees.mine(student).credit()).isEqualByComparingTo("0");
    }
}
