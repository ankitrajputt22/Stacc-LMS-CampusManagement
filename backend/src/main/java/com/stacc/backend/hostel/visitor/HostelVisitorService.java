package com.stacc.backend.hostel.visitor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hostel visitors. Residents register visitors for today or the next 30 days and cancel them while expected; security
 * records arrivals (only within the hostel's visiting hours, in the college's time zone), departures, and walk-ins for
 * a resident by college ID; the hostel office sets visiting hours. Each change of a visit locks its row.
 */
@Service
@Transactional
public class HostelVisitorService {

    static final String NOT_RESIDENT = "Only a student with a hostel bed can register visitors.";
    static final String RESIDENT_NOT_FOUND = "No hostel resident has that college ID.";
    static final String NOT_FOUND = "Visit not found.";
    static final String OUTSIDE_HOURS = "Visitors are not taken now: it is outside the hostel's visiting hours.";
    static final String DATE_RANGE = "Choose today or a day in the next 30 days.";
    static final int AHEAD_DAYS = 30;

    public record HoursView(Long hostelId, LocalTime opensAt, LocalTime closesAt) {
    }

    public record VisitView(Long visitId, String visitorName, String relation, HostelVisit.Kind kind, LocalDate expectedOn,
            HostelVisit.Status status, Instant arrivedAt, Instant leftAt, String residentLoginId, String hostel, String room) {

        static VisitView of(HostelVisit visit) {
            HostelAllocation allocation = visit.getAllocation();
            return new VisitView(visit.getId(), visit.getVisitorName(), visit.getRelation(), visit.getKind(), visit.getExpectedOn(),
                    visit.getStatus(), visit.getArrivedAt(), visit.getLeftAt(),
                    allocation.getHostelApplication().getStudentProfile().getUserAccount().getLoginId(),
                    allocation.getBed().getRoom().getHostel().getName(), allocation.getBed().getRoom().getRoomNumber());
        }
    }

    private final HostelVisitRepository visits;
    private final HostelVisitingHoursRepository hours;
    private final HostelAllocationRepository allocations;
    private final HostelRepository hostels;
    private final UserAccountRepository accounts;
    private final EngagementProperties college;
    private final Clock clock;

    public HostelVisitorService(HostelVisitRepository visits, HostelVisitingHoursRepository hours,
            HostelAllocationRepository allocations, HostelRepository hostels, UserAccountRepository accounts,
            EngagementProperties college, Clock clock) {
        this.visits = visits;
        this.hours = hours;
        this.allocations = allocations;
        this.hostels = hostels;
        this.accounts = accounts;
        this.college = college;
        this.clock = clock;
    }

    // --- Residents

    public VisitView register(Long accountId, String visitorName, String relation, LocalDate on) {
        HostelAllocation allocation = allocations.findActiveForStudentAccount(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_RESIDENT));
        LocalDate today = collegeNow().toLocalDate();
        if (on == null || on.isBefore(today) || on.isAfter(today.plusDays(AHEAD_DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, DATE_RANGE);
        }
        return VisitView.of(visits.saveAndFlush(valid(() -> HostelVisit.preRegistered(allocation, visitorName, relation, on, now()))));
    }

    @Transactional(readOnly = true)
    public List<VisitView> mine(Long accountId) {
        return visits.findOwnFrom(accountId, collegeNow().toLocalDate().minusDays(30)).stream().map(VisitView::of).toList();
    }

    public VisitView cancel(Long accountId, Long visitId) {
        HostelVisit visit = locked(visitId);
        if (!visit.getAllocation().getHostelApplication().getStudentProfile().getUserAccount().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        valid(() -> {
            visit.cancel();
            return visit;
        });
        return VisitView.of(visit);
    }

    // --- Security

    @Transactional(readOnly = true)
    public List<VisitView> today() {
        return visits.findForDay(collegeNow().toLocalDate()).stream().map(VisitView::of).toList();
    }

    public VisitView arrive(Long securityAccountId, Long visitId) {
        HostelVisit visit = locked(visitId);
        if (!visit.getExpectedOn().equals(collegeNow().toLocalDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "This visitor is expected on another day.");
        }
        requireVisitingHours(visit.getAllocation());
        UserAccount by = accounts.getReferenceById(securityAccountId);
        valid(() -> {
            visit.arrive(by, now());
            return visit;
        });
        return VisitView.of(visit);
    }

    public VisitView leave(Long visitId) {
        HostelVisit visit = locked(visitId);
        valid(() -> {
            visit.leave(now());
            return visit;
        });
        return VisitView.of(visit);
    }

    /** A visitor who was not registered, for the resident with this college ID, within visiting hours. */
    public VisitView walkIn(Long securityAccountId, String residentLoginId, String visitorName, String relation) {
        Long residentId = (residentLoginId == null ? Optional.<UserAccount>empty() : accounts.findByLoginId(residentLoginId.strip()))
                .map(UserAccount::getId).orElse(null);
        HostelAllocation allocation = (residentId == null ? Optional.<HostelAllocation>empty() : allocations.findActiveForStudentAccount(residentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RESIDENT_NOT_FOUND));
        requireVisitingHours(allocation);
        UserAccount by = accounts.getReferenceById(securityAccountId);
        return VisitView.of(visits.saveAndFlush(valid(() ->
                HostelVisit.walkIn(allocation, visitorName, relation, collegeNow().toLocalDate(), by, now()))));
    }

    // --- The hostel office

    @Transactional(readOnly = true)
    public Optional<HoursView> hoursOf(Long hostelId) {
        return hours.findById(hostelId).map(found -> new HoursView(found.getHostelId(), found.getOpensAt(), found.getClosesAt()));
    }

    public HoursView setHours(Long hostelId, LocalTime opens, LocalTime closes) {
        if (hostelId == null || !hostels.existsById(hostelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Hostel not found.");
        }
        HostelVisitingHours saved = hours.findById(hostelId).map(found -> {
            valid(() -> {
                found.change(opens, closes, now());
                return found;
            });
            return found;
        }).orElseGet(() -> hours.save(valid(() -> new HostelVisitingHours(hostelId, opens, closes, now()))));
        hours.flush();
        return new HoursView(saved.getHostelId(), saved.getOpensAt(), saved.getClosesAt());
    }

    // --- Helpers

    private void requireVisitingHours(HostelAllocation allocation) {
        Long hostelId = allocation.getBed().getRoom().getHostel().getId();
        boolean open = hours.findById(hostelId).map(found -> found.contains(collegeNow().toLocalTime())).orElse(false);
        if (!open) {
            throw new ApiException(HttpStatus.CONFLICT, OUTSIDE_HOURS);
        }
    }

    private HostelVisit locked(Long visitId) {
        return (visitId == null ? Optional.<HostelVisit>empty() : visits.findByIdForUpdate(visitId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private ZonedDateTime collegeNow() {
        return ZonedDateTime.now(clock.withZone(college.zone()));
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
