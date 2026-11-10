package com.stacc.backend.engagement.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.events.CampusEventService.Viewer;
import com.stacc.backend.pass.Pass;
import com.stacc.backend.pass.PassPurpose;
import com.stacc.backend.pass.PassService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event attendance (v0.11.6). A registered attendee gets a check-in code from the pass service, valid from an hour
 * before the event until it ends; the event's managers check attendees in by that code or by college ID within the
 * same window. Attending is recorded once on the registration under its row lock. A participation certificate is due
 * to every attendee once the event has ended; that is worked out on read, never stored. A trial (v0.11.8) is an event
 * marked as one before it starts; once it has started its managers record Selected or Not selected per attendee.
 */
@Service
@Transactional
public class EventAttendanceService {

    static final Duration EARLY = Duration.ofHours(1);
    static final String NOT_FOUND = "Event not found.";
    static final String NOT_REGISTERED = "You are not registered for this event.";
    static final String OUTSIDE_WINDOW = "Check-in opens an hour before the event and closes when it ends.";
    static final String UNKNOWN_CODE = "That code is not a check-in code for this event.";
    static final String NOT_ATTENDEE = "That student is not registered for this event.";
    static final String NOT_TRIAL = "This event is not a trial.";
    static final String TRIAL_NOT_STARTED = "Outcomes are recorded once the trial has started.";

    public record CheckInCode(String code, Instant validFrom, Instant validUntil) {
    }

    public record MyAttendance(boolean registered, Instant attendedAt, boolean certificateEligible,
            CampusEventRegistration.TrialOutcome trialOutcome) {
    }

    public record Attendee(String loginId, String status, Instant registeredAt, Instant attendedAt,
            CampusEventRegistration.CheckInMethod checkInMethod, boolean certificateEligible,
            CampusEventRegistration.TrialOutcome trialOutcome) {
    }

    public record CheckInResult(String loginId, Instant attendedAt, boolean alreadyCheckedIn) {
    }

    private final CampusEventRepository events;
    private final CampusEventRegistrationRepository registrations;
    private final CampusEventService eventService;
    private final PassService passes;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public EventAttendanceService(CampusEventRepository events, CampusEventRegistrationRepository registrations,
            CampusEventService eventService, PassService passes, UserAccountRepository accounts, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.eventService = eventService;
        this.passes = passes;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** A fresh check-in code for the attendee's own registration; earlier codes for it stop working. */
    public CheckInCode myCode(Long accountId, Long eventId) {
        CampusEventRegistration registration = registrations.findCurrentForAccount(eventId, accountId)
                .filter(found -> found.getStatus() == CampusEventRegistration.Status.REGISTERED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_REGISTERED));
        CampusEvent event = registration.getEvent();
        PassService.IssuedPass issued = passes.issue(PassPurpose.EVENT_CHECK_IN, registration.getId(), accountId,
                event.getStartsAt().minus(EARLY), event.getEndsAt());
        return new CheckInCode(issued.code(), issued.pass().getValidFrom(), issued.pass().getValidUntil());
    }

    @Transactional(readOnly = true)
    public MyAttendance mine(Long accountId, Long eventId) {
        Optional<CampusEventRegistration> registration = registrations.findCurrentForAccount(eventId, accountId)
                .filter(found -> found.getStatus() == CampusEventRegistration.Status.REGISTERED);
        return registration.map(found -> new MyAttendance(true, found.getAttendedAt(), eligible(found), found.getTrialOutcome()))
                .orElse(new MyAttendance(false, null, false, null));
    }

    /** The event's registrations with attendance, for those who manage it. */
    @Transactional(readOnly = true)
    public List<Attendee> attendees(Viewer viewer, Long eventId) {
        managed(viewer, eventId);
        return registrations.findCurrent(eventId).stream().map(found -> new Attendee(found.getAccount().getLoginId(), found.getStatus().name(),
                found.getRegisteredAt(), found.getAttendedAt(), found.getCheckInMethod(), eligible(found), found.getTrialOutcome())).toList();
    }

    /** Checks in the attendee whose check-in code this is, within the window. */
    public CheckInResult checkInByCode(Viewer viewer, Long eventId, String code) {
        CampusEvent event = managed(viewer, eventId);
        Pass pass = passes.find(code).filter(found -> found.getPurpose() == PassPurpose.EVENT_CHECK_IN && found.getRevokedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, UNKNOWN_CODE));
        CampusEventRegistration registration = registrations.findByIdForUpdate(pass.getSourceId())
                .filter(found -> found.getEvent().getId().equals(event.getId()))
                .filter(found -> found.getAccount().getId().equals(pass.getHolderAccountId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, UNKNOWN_CODE));
        return checkIn(viewer, event, registration, CampusEventRegistration.CheckInMethod.PASS);
    }

    /** Checks in a registered attendee by college ID, within the window. */
    public CheckInResult checkInByLoginId(Viewer viewer, Long eventId, String loginId) {
        CampusEvent event = managed(viewer, eventId);
        Long accountId = (loginId == null ? Optional.<UserAccount>empty() : accounts.findByLoginId(loginId.strip()))
                .map(UserAccount::getId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        Long registrationId = registrations.findCurrentForAccount(eventId, accountId).map(CampusEventRegistration::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        CampusEventRegistration registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        return checkIn(viewer, event, registration, CampusEventRegistration.CheckInMethod.MANUAL);
    }

    private CheckInResult checkIn(Viewer viewer, CampusEvent event, CampusEventRegistration registration,
            CampusEventRegistration.CheckInMethod method) {
        Instant now = now();
        if (now.isBefore(event.getStartsAt().minus(EARLY)) || !now.isBefore(event.getEndsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, OUTSIDE_WINDOW);
        }
        boolean first;
        try {
            first = registration.checkIn(method, accounts.getReferenceById(viewer.accountId()), now);
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
        registrations.flush();
        return new CheckInResult(registration.getAccount().getLoginId(), registration.getAttendedAt(), !first);
    }

    // --- Trials (v0.11.8)

    /** Marks a managed event as a trial before it starts; it stays one. */
    public void markAsTrial(Viewer viewer, Long eventId) {
        CampusEvent event = managed(viewer, eventId);
        CampusEvent locked = events.findByIdForUpdate(event.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        try {
            locked.markAsTrial(now());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }

    /** Records or corrects Selected or Not selected for a registered attendee of a trial that has started. */
    public Attendee decideTrial(Viewer viewer, Long eventId, String loginId, CampusEventRegistration.TrialOutcome outcome) {
        CampusEvent event = managed(viewer, eventId);
        if (!event.isTrial()) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_TRIAL);
        }
        if (now().isBefore(event.getStartsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, TRIAL_NOT_STARTED);
        }
        Long accountId = (loginId == null ? Optional.<UserAccount>empty() : accounts.findByLoginId(loginId.strip()))
                .map(UserAccount::getId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        Long registrationId = registrations.findCurrentForAccount(eventId, accountId).map(CampusEventRegistration::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        CampusEventRegistration registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_ATTENDEE));
        try {
            registration.decideTrial(outcome, accounts.getReferenceById(viewer.accountId()), now());
        } catch (IllegalStateException | IllegalArgumentException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
        registrations.flush();
        return new Attendee(registration.getAccount().getLoginId(), registration.getStatus().name(), registration.getRegisteredAt(),
                registration.getAttendedAt(), registration.getCheckInMethod(), eligible(registration), registration.getTrialOutcome());
    }

    private boolean eligible(CampusEventRegistration registration) {
        return registration.getAttendedAt() != null && !now().isBefore(registration.getEvent().getEndsAt());
    }

    private CampusEvent managed(Viewer viewer, Long eventId) {
        CampusEvent event = (eventId == null ? Optional.<CampusEvent>empty() : events.findById(eventId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!eventService.canManage(event, viewer)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return event;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
