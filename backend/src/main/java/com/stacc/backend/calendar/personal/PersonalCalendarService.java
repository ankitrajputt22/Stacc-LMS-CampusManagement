package com.stacc.backend.calendar.personal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's own calendar entries and private subscription link. Entries belong to the account in the token and are
 * never shown to anyone else; another person's entry answers 404. The link's secret is made here, shown once, and only
 * its fingerprint is stored; creating a new link revokes the old one.
 */
@Service
public class PersonalCalendarService {

    public static final String EVENT_UNAVAILABLE = "Event not found.";
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record FeedStatus(boolean active, Instant createdAt) {
    }

    /** A new link's secret. It is never stored and cannot be shown again. */
    public record NewFeed(String token, Instant createdAt) {
    }

    private final PersonalCalendarEventRepository events;
    private final CalendarFeedLinkRepository links;
    private final Clock clock;

    public PersonalCalendarService(PersonalCalendarEventRepository events, CalendarFeedLinkRepository links, Clock clock) {
        this.events = events;
        this.links = links;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PersonalCalendarEvent> overlapping(Long accountId, LocalDate from, LocalDate to) {
        return events.findOverlapping(accountId, from, to);
    }

    @Transactional
    public PersonalCalendarEvent create(Long accountId, String title, String notes, LocalDate startDate, LocalDate endDate) {
        try {
            return events.saveAndFlush(new PersonalCalendarEvent(accountId, title, notes, startDate, endDate, now()));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    @Transactional
    public PersonalCalendarEvent change(Long accountId, Long eventId, String title, String notes, LocalDate startDate, LocalDate endDate) {
        PersonalCalendarEvent event = own(accountId, eventId);
        try {
            event.change(title, notes, startDate, endDate, now());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        return event;
    }

    /** Personal entries are the person's own notes, so the person may remove them for good. */
    @Transactional
    public void delete(Long accountId, Long eventId) {
        events.delete(own(accountId, eventId));
    }

    @Transactional(readOnly = true)
    public FeedStatus feedStatus(Long accountId) {
        return links.findActive(accountId).map(link -> new FeedStatus(true, link.getCreatedAt())).orElse(new FeedStatus(false, null));
    }

    @Transactional
    public NewFeed createFeed(Long accountId) {
        Instant now = now();
        links.findActiveForUpdate(accountId).ifPresent(link -> {
            link.revoke(now);
            links.saveAndFlush(link);
        });
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        links.saveAndFlush(new CalendarFeedLink(accountId, fingerprint(token), now));
        return new NewFeed(token, now);
    }

    @Transactional
    public void revokeFeed(Long accountId) {
        links.findActiveForUpdate(accountId).ifPresent(link -> link.revoke(now()));
    }

    /** The account behind an active link, or empty for anything else. Only reads. */
    @Transactional(readOnly = true)
    public Optional<Long> accountForFeed(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return links.findActiveByFingerprint(fingerprint(token)).map(CalendarFeedLink::getUserAccountId);
    }

    private PersonalCalendarEvent own(Long accountId, Long eventId) {
        return (eventId == null ? Optional.<PersonalCalendarEvent>empty() : events.findByIdAndUserAccountId(eventId, accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, EVENT_UNAVAILABLE));
    }

    static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
