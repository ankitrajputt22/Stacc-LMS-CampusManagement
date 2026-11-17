package com.stacc.backend.academic.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.Concurrently;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** An approval and a rejection of the same request at once: the request row lock lets one decide. A student of its own. */
class StudentRequestDecisionMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private StudentRequestService requests;

    @Autowired
    private UserAccountRepository accounts;

    @Test
    void aRequestIsDecidedOnce() throws Exception {
        Long student = accounts.findByLoginId("cse25015").orElseThrow().getId();
        Long office = accounts.findByLoginId("adm1001").orElseThrow().getId();
        LocalDate today = LocalDate.now();
        Long requestId = requests.medicalLeave(student, today, today.plusDays(1), "Fever, advised rest").requestId();

        List<Concurrently.Outcome<StudentRequestService.RequestView>> outcomes = Concurrently.run(
                () -> requests.approve(office, requestId, null, null),
                () -> requests.reject(office, requestId, "Certificate missing"));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(requests.mine(student)).filteredOn(view -> view.requestId().equals(requestId))
                .singleElement().extracting(StudentRequestService.RequestView::status)
                .isIn(StudentRequest.Status.APPROVED, StudentRequest.Status.REJECTED);
    }
}
