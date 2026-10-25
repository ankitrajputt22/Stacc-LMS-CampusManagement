package com.stacc.backend.support.helpdesk;

import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Made-up accounts, categories, and tickets for the helpdesk tests, with IDs set the way the database would. */
final class HelpdeskFixtures {

    private HelpdeskFixtures() {
    }

    static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static UserAccount account(long id, String loginId, RoleName... roles) {
        UserAccount account = withId(new UserAccount(loginId, "not-a-real-hash"), id);
        for (RoleName role : roles) {
            account.assignRole(new Role(role));
        }
        return account;
    }

    static HelpdeskCategory category(long id, String code, boolean active) {
        HelpdeskCategory category = withId(new HelpdeskCategory(code, "Category " + code, null), id);
        category.update(category.getName(), null, active);
        return category;
    }

    static HelpdeskTicket ticket(long id, UserAccount requester, HelpdeskCategory category) {
        HelpdeskTicket ticket = withId(new HelpdeskTicket(requester, category, "Cannot open my course",
                "The course is listed on my enrollment but not under Courses.", HelpdeskTicketPriority.NORMAL), id);
        ticket.assignNumber();
        return ticket;
    }

    /** The helpdesk part of a requester's token: STUDENT, FACULTY, or ADMIN with the three "own" permissions. */
    static HelpdeskActor requester(long accountId, String role) {
        return HelpdeskActor.of(accountId, AuthorityUtils.createAuthorityList(role, HelpdeskActor.CREATE,
                HelpdeskActor.VIEW_OWN, HelpdeskActor.REPLY_OWN));
    }

    /** An admin with every helpdesk permission, as V46 grants. */
    static HelpdeskActor admin(long accountId) {
        return HelpdeskActor.of(accountId, AuthorityUtils.createAuthorityList("ROLE_ADMIN", HelpdeskActor.CREATE,
                HelpdeskActor.VIEW_OWN, HelpdeskActor.REPLY_OWN, HelpdeskActor.VIEW_ALL, HelpdeskActor.MANAGE,
                HelpdeskActor.REPLY_ALL));
    }

    static HelpdeskActor actor(long accountId, String... authorities) {
        return HelpdeskActor.of(accountId, List.copyOf(AuthorityUtils.createAuthorityList(authorities)));
    }
}
