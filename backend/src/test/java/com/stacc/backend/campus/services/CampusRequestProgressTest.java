package com.stacc.backend.campus.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.campus.CampusFixtures;
import org.junit.jupiter.api.Test;

/** An accepted request moves forward only: being processed, ready, completed; nothing goes back. */
class CampusRequestProgressTest {

    private final UserAccount student = CampusFixtures.account(5, "student1", RoleName.STUDENT);
    private final CampusServiceDefinition direct = CampusFixtures.service(1, "BONAFIDE", true, false, null);

    @Test
    void anAcceptedRequestIsProcessedThenReadyThenCompleted() {
        CampusServiceRequest request = CampusFixtures.request(41, direct, student, null);
        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.APPROVED);

        assertThat(request.startProcessing(CampusFixtures.NOW)).isTrue();
        assertThat(request.startProcessing(CampusFixtures.NOW.plusSeconds(5))).isFalse();
        assertThat(request.getProcessingAt()).isNotNull();
        assertThat(request.markReady(CampusFixtures.NOW)).isTrue();
        assertThat(request.markReady(CampusFixtures.NOW)).isFalse();
        assertThatThrownBy(() -> request.startProcessing(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::cancel).isInstanceOf(IllegalStateException.class);

        request.complete(CampusFixtures.NOW);
        assertThat(request.getStatus()).isEqualTo(CampusServiceRequestStatus.COMPLETED);
        assertThatThrownBy(() -> request.markReady(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRequestWaitingForApprovalCannotBeProcessed() {
        CampusServiceDefinition withApproval = CampusFixtures.service(2, "HOSTEL_LETTER", true, false, "CAMPUS_SERVICE_REQUEST");
        CampusServiceRequest request = CampusFixtures.request(42, withApproval, student, CampusFixtures.approval(7, student));
        assertThatThrownBy(() -> request.startProcessing(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.markReady(CampusFixtures.NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(CampusServiceRequestStatus.READY.isBeingHandled()).isTrue();
        assertThat(CampusServiceRequestStatus.SUBMITTED.isBeingHandled()).isFalse();
    }
}
