package com.stacc.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.approval.ApprovalResponses.ApprovalDecisionResponse;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two approvers deciding the same step at the same moment, on MySQL: the request row lock lets exactly one decision
 * through, and the other gets 409 instead of a second decision on top of the first. Uses the demo requests demo-1 and
 * demo-2, which wait at their faculty step and which no other MySQL test touches.
 */
class ApprovalDecisionLocksMySqlIT extends MySqlIntegrationTest {

    private static final String DEMO_SOURCE = "DEV_DEMO";

    @Autowired
    private ApprovalWorkflowService approvals;

    @Autowired
    private ApprovalRequestRepository requests;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoApprovalsAtOnceDecideTheStepOnce() throws Exception {
        Long requestId = demoRequestId("demo-1");
        Long stepId = currentStepId(requestId);

        List<Outcome<ApprovalDecisionResponse>> outcomes = Concurrently.run(
                () -> approvals.approveStep(faculty("faculty1"), stepId, null),
                () -> approvals.approveStep(faculty("faculty2"), stepId, null));

        assertOneWonAndOneConflicted(outcomes);
        assertThat(historyCount(requestId, ApprovalRequestHistoryAction.STEP_APPROVED)).isEqualTo(1);
    }

    @Test
    void anApprovalAndARejectionAtOnceDecideTheStepOnce() throws Exception {
        Long requestId = demoRequestId("demo-2");
        Long stepId = currentStepId(requestId);

        List<Outcome<ApprovalDecisionResponse>> outcomes = Concurrently.run(
                () -> approvals.approveStep(faculty("faculty1"), stepId, null),
                () -> approvals.rejectStep(faculty("faculty2"), stepId, "Not needed."));

        assertOneWonAndOneConflicted(outcomes);
        long decisions = historyCount(requestId, ApprovalRequestHistoryAction.STEP_APPROVED)
                + historyCount(requestId, ApprovalRequestHistoryAction.STEP_REJECTED);
        assertThat(decisions).isEqualTo(1);
    }

    private static void assertOneWonAndOneConflicted(List<Outcome<ApprovalDecisionResponse>> outcomes) {
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome -> {
            assertThat(outcome.failure()).isInstanceOf(ApiException.class);
            ApiException failure = (ApiException) outcome.failure();
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(failure.getMessage()).isEqualTo(ApprovalWorkflowService.STEP_DECIDED);
        });
    }

    private ApprovalActor faculty(String loginId) {
        return ApprovalActor.of(accounts.findByLoginId(loginId).orElseThrow().getId(),
                AuthorityUtils.createAuthorityList("ROLE_FACULTY", ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE));
    }

    private Long demoRequestId(String sourceId) {
        return requests.findAllForSource(DEMO_SOURCE, sourceId).getFirst().getId();
    }

    private Long currentStepId(Long requestId) {
        return transaction.execute(status -> requests.findById(requestId).orElseThrow().currentStep().orElseThrow().getId());
    }

    private long historyCount(Long requestId, ApprovalRequestHistoryAction action) {
        return jdbc.queryForObject("select count(*) from approval_request_history where approval_request_id = ? and action = ?",
                Long.class, requestId, action.name());
    }
}
