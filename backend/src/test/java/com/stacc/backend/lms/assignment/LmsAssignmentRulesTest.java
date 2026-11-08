package com.stacc.backend.lms.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.stacc.backend.lms.assignment.submission.SubmissionFixtures;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.junit.jupiter.api.Test;

class LmsAssignmentRulesTest {

    private static final Instant DUE = SubmissionFixtures.DUE;

    private final LmsAssignment assignment = SubmissionFixtures.assignment(SubmissionFixtures.course(7), 5, LmsContentStatus.PUBLISHED);

    @Test
    void anAssignmentIsAlwaysOpenWithLateWorkAndUnlimitedAttemptsUntilRulesAreSet() {
        assertThat(assignment.submissionWindowAt(Instant.parse("2000-01-01T00:00:00Z"))).isEqualTo(AssignmentSubmissionWindow.OPEN);
        assertThat(assignment.submissionWindowAt(DUE.plusSeconds(86_400 * 365))).isEqualTo(AssignmentSubmissionWindow.OPEN);
        assertThat(assignment.isAllowLateSubmissions()).isTrue();
        assertThat(assignment.getMaxAttempts()).isNull();
    }

    @Test
    void theWindowOpensClosesAndRefusesLateWorkWhenSet() {
        assignment.changeSubmissionRules(DUE.minusSeconds(3600), DUE.plusSeconds(3600), true, 3);
        assertThat(assignment.submissionWindowAt(DUE.minusSeconds(3601))).isEqualTo(AssignmentSubmissionWindow.NOT_OPEN);
        assertThat(assignment.submissionWindowAt(DUE.plusSeconds(1800))).isEqualTo(AssignmentSubmissionWindow.OPEN);
        assertThat(assignment.submissionWindowAt(DUE.plusSeconds(3601))).isEqualTo(AssignmentSubmissionWindow.CLOSED);

        assignment.changeSubmissionRules(null, null, false, null);
        assertThat(assignment.submissionWindowAt(DUE)).isEqualTo(AssignmentSubmissionWindow.OPEN);
        assertThat(assignment.submissionWindowAt(DUE.plusMillis(1))).isEqualTo(AssignmentSubmissionWindow.CLOSED);
    }

    @Test
    void rulesMustSurroundTheDueDateAndTheDueDateStaysInside() {
        assertThatThrownBy(() -> assignment.changeSubmissionRules(DUE.plusSeconds(1), null, true, null))
                .hasMessage("The opening time cannot be after the due date.");
        assertThatThrownBy(() -> assignment.changeSubmissionRules(null, DUE.minusSeconds(1), true, null))
                .hasMessage("The closing time cannot be before the due date.");
        assertThatThrownBy(() -> assignment.changeSubmissionRules(null, null, true, 0))
                .hasMessage("The attempt limit must be between 1 and 100.");

        assignment.changeSubmissionRules(DUE.minusSeconds(60), DUE.plusSeconds(60), true, null);
        assertThatThrownBy(() -> assignment.edit("T", "I", DUE.plusSeconds(120), new BigDecimal("20")))
                .hasMessage(LmsAssignment.DUE_OUTSIDE_WINDOW);
        assignment.edit("T", "I", DUE.plusSeconds(30), new BigDecimal("20"));
        assertThat(assignment.getDueAt()).isEqualTo(DUE.plusSeconds(30));
    }
}
