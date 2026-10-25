package com.stacc.backend.support.helpdesk;

import static com.stacc.backend.support.helpdesk.HelpdeskTicketStatus.CLOSED;
import static com.stacc.backend.support.helpdesk.HelpdeskTicketStatus.IN_PROGRESS;
import static com.stacc.backend.support.helpdesk.HelpdeskTicketStatus.OPEN;
import static com.stacc.backend.support.helpdesk.HelpdeskTicketStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class HelpdeskTicketTest {

    private static final Instant T1 = Instant.parse("2026-10-20T10:15:30.123456789Z");
    private static final Instant T2 = Instant.parse("2026-10-21T08:00:00Z");

    private final UserAccount student = HelpdeskFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = HelpdeskFixtures.account(1, "admin1", RoleName.ADMIN);
    private final HelpdeskCategory lms = HelpdeskFixtures.category(3, "LMS", true);

    private HelpdeskTicket ticket() {
        return HelpdeskFixtures.ticket(42, student, lms);
    }

    @Test
    void aNewTicketIsOpenUnassignedAndNumberedFromItsId() {
        HelpdeskTicket ticket = ticket();

        assertThat(ticket.getStatus()).isEqualTo(OPEN);
        assertThat(ticket.getAssignedTo()).isNull();
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
        assertThat(ticket.getTicketNumber()).isEqualTo("STC-000042");
        assertThat(HelpdeskTicket.numberFor(1234567)).isEqualTo("STC-1234567");
    }

    @Test
    void theNumberIsGivenOnceAndNeedsAnId() {
        HelpdeskTicket ticket = ticket();
        HelpdeskFixtures.withId(ticket, 99);
        ticket.assignNumber();
        assertThat(ticket.getTicketNumber()).isEqualTo("STC-000042");

        HelpdeskTicket unsaved = new HelpdeskTicket(student, lms, "Subject", "Description", HelpdeskTicketPriority.LOW);
        assertThatThrownBy(unsaved::assignNumber).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLifecycleOnlyMovesForward() {
        assertThat(OPEN.canMoveTo(IN_PROGRESS)).isTrue();
        assertThat(OPEN.canMoveTo(RESOLVED)).isTrue();
        assertThat(OPEN.canMoveTo(CLOSED)).isTrue();
        assertThat(IN_PROGRESS.canMoveTo(RESOLVED)).isTrue();
        assertThat(IN_PROGRESS.canMoveTo(CLOSED)).isTrue();
        assertThat(RESOLVED.canMoveTo(CLOSED)).isTrue();

        assertThat(IN_PROGRESS.canMoveTo(OPEN)).isFalse();
        assertThat(RESOLVED.canMoveTo(OPEN)).isFalse();
        assertThat(RESOLVED.canMoveTo(IN_PROGRESS)).isFalse();
        assertThat(CLOSED.canMoveTo(OPEN)).isFalse();
        assertThat(CLOSED.canMoveTo(IN_PROGRESS)).isFalse();
        assertThat(CLOSED.canMoveTo(RESOLVED)).isFalse();

        for (HelpdeskTicketStatus status : HelpdeskTicketStatus.values()) {
            assertThat(status.canMoveTo(status)).as(status + " to itself").isTrue();
        }
    }

    @Test
    void resolvingAndClosingRecordTheirTimeOnceInMicroseconds() {
        HelpdeskTicket ticket = ticket();
        ticket.moveTo(IN_PROGRESS, T1);
        assertThat(ticket.getResolvedAt()).isNull();

        ticket.moveTo(RESOLVED, T1);
        assertThat(ticket.getResolvedAt()).isEqualTo(Instant.parse("2026-10-20T10:15:30.123456Z"));
        ticket.moveTo(RESOLVED, T2);
        assertThat(ticket.getResolvedAt()).isEqualTo(Instant.parse("2026-10-20T10:15:30.123456Z"));

        ticket.moveTo(CLOSED, T2);
        assertThat(ticket.getClosedAt()).isEqualTo(T2);
        assertThat(ticket.getResolvedAt()).isEqualTo(Instant.parse("2026-10-20T10:15:30.123456Z"));
        ticket.moveTo(CLOSED, T1);
        assertThat(ticket.getClosedAt()).isEqualTo(T2);
    }

    @Test
    void closingAnOpenTicketDirectlyLeavesItUnresolved() {
        HelpdeskTicket ticket = ticket();
        ticket.moveTo(CLOSED, T1);

        assertThat(ticket.isClosed()).isTrue();
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNotNull();
    }

    @Test
    void aBackwardMoveIsRefused() {
        HelpdeskTicket ticket = ticket();
        ticket.moveTo(RESOLVED, T1);

        assertThatThrownBy(() -> ticket.moveTo(OPEN, T2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ticket.moveTo(IN_PROGRESS, T2)).isInstanceOf(IllegalStateException.class);
        assertThat(ticket.getStatus()).isEqualTo(RESOLVED);
    }

    @Test
    void aClosedTicketRefusesEveryChange() {
        HelpdeskTicket ticket = ticket();
        ticket.moveTo(CLOSED, T1);

        assertThatThrownBy(() -> ticket.changePriority(HelpdeskTicketPriority.HIGH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ticket.assignTo(admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ticket.moveTo(OPEN, T2)).isInstanceOf(IllegalStateException.class);
        assertThat(ticket.getPriority()).isEqualTo(HelpdeskTicketPriority.NORMAL);
        assertThat(ticket.getAssignedTo()).isNull();
    }

    @Test
    void assignmentAndPriorityChangeAnOpenTicket() {
        HelpdeskTicket ticket = ticket();
        ticket.assignTo(admin);
        ticket.changePriority(HelpdeskTicketPriority.URGENT);
        assertThat(ticket.getAssignedTo()).isSameAs(admin);
        assertThat(ticket.getPriority()).isEqualTo(HelpdeskTicketPriority.URGENT);

        ticket.assignTo(null);
        assertThat(ticket.getAssignedTo()).isNull();
    }

    @Test
    void textIsRequiredAndLimited() {
        assertThatThrownBy(() -> new HelpdeskTicket(student, lms, " ", "Description", HelpdeskTicketPriority.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HelpdeskTicket(student, lms, "x".repeat(201), "Description",
                HelpdeskTicketPriority.LOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HelpdeskTicket(student, lms, "Subject", "x".repeat(10_001),
                HelpdeskTicketPriority.LOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new HelpdeskTicket(student, lms, "Subject", "x".repeat(10_000), HelpdeskTicketPriority.LOW)
                .getDescription()).hasSize(10_000);
    }

    @Test
    void requestersMayNotChooseUrgent() {
        assertThat(HelpdeskTicketPriority.LOW.isRequesterChoice()).isTrue();
        assertThat(HelpdeskTicketPriority.NORMAL.isRequesterChoice()).isTrue();
        assertThat(HelpdeskTicketPriority.HIGH.isRequesterChoice()).isTrue();
        assertThat(HelpdeskTicketPriority.URGENT.isRequesterChoice()).isFalse();
    }

    @Test
    void categoryCodesAreStoredInUppercaseAndChecked() {
        assertThat(new HelpdeskCategory("  lms_help ", "LMS", null).getCode()).isEqualTo("LMS_HELP");
        assertThat(new HelpdeskCategory("GENERAL", "General", " ").getDescription()).isNull();
        assertThatThrownBy(() -> HelpdeskCategory.normalizedCode("1ST")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HelpdeskCategory.normalizedCode("LMS HELP")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HelpdeskCategory.normalizedCode("A".repeat(51))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new HelpdeskCategory("GENERAL", "General", null).isActive()).isTrue();
    }

    @Test
    void messagesAndHistoryAreRequiredPlainText() {
        HelpdeskTicket ticket = ticket();
        assertThatThrownBy(() -> new HelpdeskTicketMessage(ticket, student, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HelpdeskTicketMessage(ticket, student, "x".repeat(10_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new HelpdeskTicketMessage(ticket, student, "<b>Still plain</b>").getMessage())
                .isEqualTo("<b>Still plain</b>");

        HelpdeskTicketHistory entry = new HelpdeskTicketHistory(ticket, admin, HelpdeskTicketHistoryAction.STATUS_CHANGED,
                "OPEN", "IN_PROGRESS");
        assertThat(entry.getFromValue()).isEqualTo("OPEN");
        assertThat(entry.getToValue()).isEqualTo("IN_PROGRESS");
    }
}
