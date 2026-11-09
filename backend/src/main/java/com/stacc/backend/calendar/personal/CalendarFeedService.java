package com.stacc.backend.calendar.personal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.security.StaccUserPrincipal;
import com.stacc.backend.calendar.AcademicCalendarQueryService;
import com.stacc.backend.calendar.CalendarViewer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The subscription feed: the calendar of the account behind an active link, from 30 days back to 180 days ahead, built
 * with exactly the rules of the signed-in calendar from the account's current roles and permissions. A disabled
 * account, a revoked link, or an account that lost calendar access gets nothing.
 */
@Service
public class CalendarFeedService {

    private final PersonalCalendarService personal;
    private final UserAccountRepository accounts;
    private final AcademicCalendarQueryService calendar;
    private final Clock clock;

    public CalendarFeedService(PersonalCalendarService personal, UserAccountRepository accounts,
            AcademicCalendarQueryService calendar, Clock clock) {
        this.personal = personal;
        this.accounts = accounts;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<String> feed(String token) {
        Optional<UserAccount> account = personal.accountForFeed(token).flatMap(accounts::findWithRolesAndPermissionsById)
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        var authorities = StaccUserPrincipal.from(account.get(), clock.instant()).getAuthorities();
        var names = AuthorityUtils.authorityListToSet(authorities);
        if (!names.contains("ACADEMIC_CALENDAR_VIEW") || !names.contains("CALENDAR_PERSONAL_USE")) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(30);
        LocalDate to = today.plusDays(180);
        CalendarViewer viewer = CalendarViewer.of(account.get().getId(), authorities);
        return Optional.of(CalendarFeedBuilder.build(calendar.getMyCalendar(viewer, from, to, "UTC"),
                personal.overlapping(account.get().getId(), from, to), clock.instant()));
    }
}
