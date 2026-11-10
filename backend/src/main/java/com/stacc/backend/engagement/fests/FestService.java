package com.stacc.backend.engagement.fests;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.events.CampusEvent;
import com.stacc.backend.engagement.events.CampusEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fests and their volunteers. The campus office runs fests, their schedules, and shifts, and accepts or declines
 * applications; accepting locks the shift row and counts accepted volunteers with a locking read, so a shift never
 * takes more than its places. Students and faculty read published fests, apply for shifts that have not started,
 * and withdraw their own applications. Only published or cancelled events are shown on a published schedule.
 */
@Service
@Transactional
public class FestService {

    static final String FEST_NOT_FOUND = "Fest not found.";
    static final String SHIFT_NOT_FOUND = "Shift not found.";
    static final String APPLICATION_NOT_FOUND = "Application not found.";
    static final String FULL = "This shift has no free places left.";
    static final String ALREADY_APPLIED = "You have already applied for this shift.";

    public record ScheduleItem(Long eventId, String title, String venue, Instant startsAt, Instant endsAt, String status) {
    }

    public record ShiftView(Long shiftId, String roleTitle, Instant startsAt, Instant endsAt, int places, long accepted,
            Long myApplicationId, FestVolunteer.Status myApplication) {
    }

    public record FestView(Long festId, String name, String description, LocalDate startsOn, LocalDate endsOn,
            CampusFest.Status status, List<ScheduleItem> schedule, List<ShiftView> shifts) {
    }

    public record ApplicationView(Long applicationId, Long shiftId, String roleTitle, String loginId, String note,
            FestVolunteer.Status status, Instant appliedAt) {
    }

    public record FestInput(String name, String description, LocalDate startsOn, LocalDate endsOn) {
    }

    private final CampusFestRepository fests;
    private final FestShiftRepository shifts;
    private final FestVolunteerRepository volunteers;
    private final CampusEventRepository events;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public FestService(CampusFestRepository fests, FestShiftRepository shifts, FestVolunteerRepository volunteers,
            CampusEventRepository events, UserAccountRepository accounts, Clock clock) {
        this.fests = fests;
        this.shifts = shifts;
        this.volunteers = volunteers;
        this.events = events;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- Students and faculty

    @Transactional(readOnly = true)
    public List<FestView> published(Long accountId) {
        return fests.findPublished().stream().map(fest -> view(fest, accountId, true)).toList();
    }

    public FestView apply(Long accountId, Long shiftId, String note) {
        FestShift shift = (shiftId == null ? Optional.<FestShift>empty() : shifts.findById(shiftId))
                .filter(found -> found.getFest().getStatus() == CampusFest.Status.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SHIFT_NOT_FOUND));
        if (!now().isBefore(shift.getStartsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "This shift has already started.");
        }
        try {
            volunteers.saveAndFlush(valid(() -> new FestVolunteer(shift, accounts.getReferenceById(accountId), note, now())));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_APPLIED);
        }
        return view(shift.getFest(), accountId, true);
    }

    public FestView withdraw(Long accountId, Long applicationId) {
        FestVolunteer application = (applicationId == null ? Optional.<FestVolunteer>empty() : volunteers.findByIdForUpdate(applicationId))
                .filter(found -> found.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND));
        valid(() -> {
            application.withdraw();
            return application;
        });
        return view(application.getShift().getFest(), accountId, true);
    }

    // --- The campus office

    @Transactional(readOnly = true)
    public List<FestView> all() {
        return fests.findAllNewestFirst().stream().map(fest -> view(fest, null, false)).toList();
    }

    public FestView create(Long adminId, FestInput input) {
        return view(fests.saveAndFlush(valid(() -> new CampusFest(input.name(), input.description(), input.startsOn(), input.endsOn(), adminId, now()))),
                null, false);
    }

    public FestView edit(Long festId, FestInput input) {
        CampusFest fest = locked(festId);
        valid(() -> {
            fest.edit(input.name(), input.description(), input.startsOn(), input.endsOn());
            return fest;
        });
        return view(fest, null, false);
    }

    public FestView publish(Long festId) {
        CampusFest fest = locked(festId);
        valid(() -> {
            fest.publish();
            return fest;
        });
        return view(fest, null, false);
    }

    public FestView archive(Long festId) {
        CampusFest fest = locked(festId);
        fest.archive();
        return view(fest, null, false);
    }

    /** Puts a campus event on the fest's schedule; an event belongs to at most one fest. */
    public FestView schedule(Long festId, Long eventId) {
        CampusFest fest = locked(festId);
        if (eventId == null || !events.existsById(eventId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Event not found.");
        }
        valid(() -> {
            fest.schedule(eventId);
            return fest;
        });
        try {
            fests.flush();
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, "This event is already on a fest's schedule.");
        }
        return view(fest, null, false);
    }

    public FestView unschedule(Long festId, Long eventId) {
        CampusFest fest = locked(festId);
        fest.unschedule(eventId);
        fests.flush();
        return view(fest, null, false);
    }

    public FestView addShift(Long festId, String roleTitle, Instant startsAt, Instant endsAt, int places) {
        CampusFest fest = locked(festId);
        if (fest.getStatus() == CampusFest.Status.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "This fest is archived and can no longer be changed.");
        }
        shifts.saveAndFlush(valid(() -> new FestShift(fest, roleTitle, startsAt, endsAt, places)));
        return view(fest, null, false);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> applications(Long festId) {
        CampusFest fest = fests.findById(festId == null ? -1L : festId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FEST_NOT_FOUND));
        List<Long> shiftIds = shifts.findForFest(fest.getId()).stream().map(FestShift::getId).toList();
        if (shiftIds.isEmpty()) {
            return List.of();
        }
        return volunteers.findForShifts(shiftIds).stream().map(found -> new ApplicationView(found.getId(), found.getShift().getId(),
                found.getShift().getRoleTitle(), found.getAccount().getLoginId(), found.getNote(), found.getStatus(), found.getAppliedAt())).toList();
    }

    /** Accepts (within the shift's places) or declines an open application. */
    public ApplicationView decide(Long applicationId, boolean accept) {
        Long shiftId = (applicationId == null ? Optional.<FestVolunteer>empty() : volunteers.findById(applicationId))
                .map(found -> found.getShift().getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND));
        FestShift shift = shifts.findByIdForUpdate(shiftId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SHIFT_NOT_FOUND));
        FestVolunteer application = volunteers.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND));
        if (accept && volunteers.findAcceptedForUpdate(shiftId).size() >= shift.getPlaces()) {
            throw new ApiException(HttpStatus.CONFLICT, FULL);
        }
        valid(() -> {
            application.decide(accept, now());
            return application;
        });
        volunteers.flush();
        return new ApplicationView(application.getId(), shift.getId(), shift.getRoleTitle(), application.getAccount().getLoginId(),
                application.getNote(), application.getStatus(), application.getAppliedAt());
    }

    // --- Helpers

    private FestView view(CampusFest fest, Long viewerId, boolean publicSchedule) {
        Map<Long, CampusEvent> scheduled = fest.getEventIds().isEmpty() ? Map.of()
                : events.findAllById(fest.getEventIds()).stream().collect(Collectors.toMap(CampusEvent::getId, event -> event));
        List<ScheduleItem> schedule = scheduled.values().stream()
                .filter(event -> !publicSchedule || "PUBLISHED".equals(event.getStatus().name()) || "CANCELLED".equals(event.getStatus().name()))
                .sorted(Comparator.comparing(CampusEvent::getStartsAt))
                .map(event -> new ScheduleItem(event.getId(), event.getTitle(), event.getVenue(), event.getStartsAt(), event.getEndsAt(),
                        event.getStatus().name()))
                .toList();
        List<FestShift> festShifts = shifts.findForFest(fest.getId());
        List<FestVolunteer> applied = festShifts.isEmpty() ? List.of() : volunteers.findForShifts(festShifts.stream().map(FestShift::getId).toList());
        List<ShiftView> shiftViews = festShifts.stream().map(shift -> {
            long accepted = applied.stream().filter(found -> found.getShift().getId().equals(shift.getId())
                    && found.getStatus() == FestVolunteer.Status.ACCEPTED).count();
            Optional<FestVolunteer> mine = viewerId == null ? Optional.empty() : applied.stream()
                    .filter(found -> found.getShift().getId().equals(shift.getId()) && found.getAccount().getId().equals(viewerId)
                            && (found.getStatus() == FestVolunteer.Status.APPLIED || found.getStatus() == FestVolunteer.Status.ACCEPTED))
                    .findFirst();
            return new ShiftView(shift.getId(), shift.getRoleTitle(), shift.getStartsAt(), shift.getEndsAt(), shift.getPlaces(), accepted,
                    mine.map(FestVolunteer::getId).orElse(null), mine.map(FestVolunteer::getStatus).orElse(null));
        }).toList();
        return new FestView(fest.getId(), fest.getName(), fest.getDescription(), fest.getStartsOn(), fest.getEndsOn(), fest.getStatus(),
                schedule, shiftViews);
    }

    private CampusFest locked(Long festId) {
        return (festId == null ? Optional.<CampusFest>empty() : fests.findByIdForUpdate(festId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FEST_NOT_FOUND));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }
}
