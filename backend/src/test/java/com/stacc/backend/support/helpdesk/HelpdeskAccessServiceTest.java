package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class HelpdeskAccessServiceTest {

    private final HelpdeskAccessService access = new HelpdeskAccessService();
    private final HelpdeskTicket ticket = HelpdeskFixtures.ticket(42,
            HelpdeskFixtures.account(70, "student1", RoleName.STUDENT), HelpdeskFixtures.category(3, "LMS", true));

    @Test
    void theRequesterSeesAndAnswersOnlyTheirOwnTicket() {
        HelpdeskActor owner = HelpdeskFixtures.requester(70, "ROLE_STUDENT");
        HelpdeskActor otherStudent = HelpdeskFixtures.requester(71, "ROLE_STUDENT");

        assertThat(access.canView(owner, ticket)).isTrue();
        assertThat(access.canReply(owner, ticket)).isTrue();
        assertThat(access.canManage(owner)).isFalse();
        assertThat(access.canView(otherStudent, ticket)).isFalse();
        assertThat(access.canReply(otherStudent, ticket)).isFalse();
    }

    @Test
    void theOwnPermissionsAreNeededEvenForTheOwner() {
        assertThat(access.canView(HelpdeskFixtures.actor(70, "ROLE_STUDENT"), ticket)).isFalse();
        assertThat(access.canReply(HelpdeskFixtures.actor(70, "ROLE_STUDENT", HelpdeskActor.VIEW_OWN), ticket)).isFalse();
    }

    @Test
    void adminsReachEveryTicketOnlyThroughTheExplicitPermissions() {
        assertThat(access.canView(HelpdeskFixtures.admin(1), ticket)).isTrue();
        assertThat(access.canReply(HelpdeskFixtures.admin(1), ticket)).isTrue();
        assertThat(access.canManage(HelpdeskFixtures.admin(1))).isTrue();

        HelpdeskActor plainAdmin = HelpdeskFixtures.requester(1, "ROLE_ADMIN");
        assertThat(access.canView(plainAdmin, ticket)).isFalse();
        assertThat(access.canReply(plainAdmin, ticket)).isFalse();
        assertThat(access.canManage(plainAdmin)).isFalse();

        HelpdeskActor viewOnly = HelpdeskFixtures.actor(1, "ROLE_ADMIN", HelpdeskActor.VIEW_ALL);
        assertThat(access.canView(viewOnly, ticket)).isTrue();
        assertThat(access.canReply(viewOnly, ticket)).isFalse();
    }

    @Test
    void theAllPermissionsWithoutTheAdminRoleOpenNothing() {
        HelpdeskActor notAdmin = HelpdeskFixtures.actor(80, "ROLE_FACULTY", HelpdeskActor.VIEW_ALL,
                HelpdeskActor.REPLY_ALL, HelpdeskActor.MANAGE);

        assertThat(access.canView(notAdmin, ticket)).isFalse();
        assertThat(access.canReply(notAdmin, ticket)).isFalse();
        assertThat(access.canManage(notAdmin)).isFalse();
    }
}
