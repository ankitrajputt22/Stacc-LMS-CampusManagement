package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelRequests.CreateHostelApplicationRequest;
import com.stacc.backend.hostel.HostelResponses.AvailableHostelBed;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.hostel.HostelResponses.HostelApplicationDetail;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The hostel's row-lock rules under truly simultaneous requests on MySQL: a student gets one current application per
 * session, a bed one active allocation, and an application one active allocation, whoever clicks first. Uses the demo
 * accounts student1 and studentfaculty1, which no other MySQL test touches.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HostelLocksMySqlIT extends MySqlIntegrationTest {

    private static Long firstApplicationId;
    private static Long secondApplicationId;

    @Autowired
    private HostelApplicationService applications;

    @Autowired
    private HostelAllocationService allocations;

    @Autowired
    private ApprovalWorkflowService approvals;

    @Autowired
    private ApprovalRequestRepository approvalRequests;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Order(1)
    void twoApplicationsAtOnceForTheSameSessionMakeOne() throws Exception {
        Long student = accountId("student1");
        CreateHostelApplicationRequest request = new CreateHostelApplicationRequest(currentSessionId(student), null);

        List<Outcome<HostelApplicationDetail>> outcomes = Concurrently.run(
                () -> applications.apply(student, request),
                () -> applications.apply(student, request));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertConflict(outcomes, HostelApplicationService.DUPLICATE);
        firstApplicationId = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().result().applicationId();

        Long other = accountId("studentfaculty1");
        secondApplicationId = applications.apply(other, new CreateHostelApplicationRequest(currentSessionId(other), null))
                .applicationId();
    }

    @Test
    @Order(2)
    void twoAllocationsAtOnceNeverShareABedOrAnApplication() throws Exception {
        approve(firstApplicationId);
        approve(secondApplicationId);
        Long admin = accountId("admin1");
        List<AvailableHostelBed> free = allocations.availableBeds(null, null);
        assertThat(free).hasSizeGreaterThanOrEqualTo(2);
        Long contestedBed = free.get(0).bedId();
        Long otherBed = free.get(1).bedId();

        // Two students, one bed.
        List<Outcome<HostelAllocationResponse>> sameBed = Concurrently.run(
                () -> allocations.allocate(admin, firstApplicationId, contestedBed),
                () -> allocations.allocate(admin, secondApplicationId, contestedBed));

        assertThat(sameBed).filteredOn(Outcome::succeeded).hasSize(1);
        assertConflict(sameBed, HostelAllocationService.BED_TAKEN);
        Long waiting = sameBed.get(0).succeeded() ? secondApplicationId : firstApplicationId;

        // One application, two beds: the student ends up in exactly one of them.
        Long anotherBed = allocations.availableBeds(null, null).stream()
                .map(AvailableHostelBed::bedId)
                .filter(bed -> !bed.equals(otherBed))
                .findFirst().orElseThrow();
        List<Outcome<HostelAllocationResponse>> sameApplication = Concurrently.run(
                () -> allocations.allocate(admin, waiting, otherBed),
                () -> allocations.allocate(admin, waiting, anotherBed));

        assertThat(sameApplication).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(sameApplication).filteredOn(outcome -> !outcome.succeeded()).singleElement()
                .satisfies(outcome -> assertThat(((ApiException) outcome.failure()).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        // And in the stored data, whatever the order was.
        assertThat(jdbc.queryForObject("""
                select count(*) from (select hostel_bed_id from hostel_allocations where status = 'ACTIVE'
                group by hostel_bed_id having count(*) > 1) shared
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from (select application.student_profile_id from hostel_allocations allocation
                join hostel_applications application on application.id = allocation.hostel_application_id
                where allocation.status = 'ACTIVE' group by application.student_profile_id having count(*) > 1) twice
                """, Long.class)).isZero();
    }

    private void approve(Long applicationId) {
        Long stepId = transaction.execute(status -> {
            List<ApprovalRequest> requests = approvalRequests.findAllForSource(
                    HostelApplicationService.SOURCE_TYPE, String.valueOf(applicationId));
            return requests.getLast().currentStep().orElseThrow().getId();
        });
        ApprovalActor admin = ApprovalActor.of(accountId("admin1"), AuthorityUtils.createAuthorityList(
                "ROLE_ADMIN", ApprovalActor.TASK_VIEW, ApprovalActor.TASK_DECIDE));
        approvals.approveStep(admin, stepId, null);
    }

    private Long currentSessionId(Long accountId) {
        return applications.eligibleSessions(accountId).stream()
                .filter(session -> "ACTIVE".equals(session.sessionStatus()))
                .findFirst().orElseThrow().academicSessionId();
    }

    private Long accountId(String loginId) {
        return accounts.findByLoginId(loginId).orElseThrow().getId();
    }

    private static void assertConflict(List<? extends Outcome<?>> outcomes, String message) {
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(outcome -> {
            assertThat(outcome.failure()).isInstanceOf(ApiException.class);
            ApiException failure = (ApiException) outcome.failure();
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(failure.getMessage()).isEqualTo(message);
        });
    }
}
