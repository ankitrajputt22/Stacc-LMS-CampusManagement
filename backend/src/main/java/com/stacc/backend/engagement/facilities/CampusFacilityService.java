package com.stacc.backend.engagement.facilities;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facilities and their bookings. A booking lies on one day inside opening hours (in the college's time zone), not on a
 * closed day, at most 12 hours, starting in the future. Booking locks the facility row and then reads overlapping
 * bookings, so two bookings of the same time never both succeed. A booking needing approval holds its time while
 * pending; a rejected or cancelled approval frees it. The booker cancels their own booking before it starts.
 */
@Service
@Transactional
public class CampusFacilityService {

    public static final String WORKFLOW_CODE = "FACILITY_BOOKING";
    public static final String SOURCE_TYPE = "FACILITY_BOOKING";

    public record FacilityView(Long facilityId, String code, String name, CampusFacility.Kind kind, String location, Integer capacity,
            LocalTime opensAt, LocalTime closesAt, boolean needsApproval, boolean active, List<ClosureView> closures) {
    }

    public record ClosureView(Long closureId, LocalDate startDate, LocalDate endDate, String reason) {
    }

    /** A booking: CONFIRMED, PENDING (waiting for approval), REJECTED, or CANCELLED, worked out on read. */
    public record BookingView(Long bookingId, Long facilityId, String facilityName, String bookedByLoginId, Instant startsAt,
            Instant endsAt, String purpose, String status) {
    }

    private final CampusFacilityRepository facilities;
    private final CampusFacilityClosureRepository closures;
    private final CampusBookingRepository bookings;
    private final UserAccountRepository accounts;
    private final ApprovalWorkflowService approvals;
    private final ApprovalQueryService approvalQueries;
    private final EngagementProperties properties;
    private final Clock clock;

    public CampusFacilityService(CampusFacilityRepository facilities, CampusFacilityClosureRepository closures,
            CampusBookingRepository bookings, UserAccountRepository accounts, ApprovalWorkflowService approvals,
            ApprovalQueryService approvalQueries, EngagementProperties properties, Clock clock) {
        this.facilities = facilities;
        this.closures = closures;
        this.bookings = bookings;
        this.accounts = accounts;
        this.approvals = approvals;
        this.approvalQueries = approvalQueries;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FacilityView> list(boolean includeInactive) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        return facilities.findAllByOrderByNameAscIdAsc().stream().filter(f -> includeInactive || f.isActive())
                .map(f -> view(f, today)).toList();
    }

    public FacilityView create(String code, String name, CampusFacility.Kind kind, String location, Integer capacity, LocalTime opensAt,
            LocalTime closesAt, boolean needsApproval) {
        CampusFacility facility;
        try {
            facility = new CampusFacility(code, name, kind, location, capacity, opensAt, closesAt, needsApproval);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The facility is not valid: " + invalid.getMessage() + ".");
        }
        if (facilities.existsByCode(facility.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "A facility with this code already exists.");
        }
        try {
            facilities.saveAndFlush(facility);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "A facility with this code already exists.");
        }
        return view(facility, LocalDate.now(clock.withZone(properties.zone())));
    }

    public FacilityView update(Long facilityId, String name, CampusFacility.Kind kind, String location, Integer capacity,
            LocalTime opensAt, LocalTime closesAt, boolean needsApproval, boolean active) {
        CampusFacility facility = locked(facilityId);
        try {
            facility.update(name, kind, location, capacity, opensAt, closesAt, needsApproval, active);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The facility is not valid: " + invalid.getMessage() + ".");
        }
        return view(facility, LocalDate.now(clock.withZone(properties.zone())));
    }

    public FacilityView close(Long facilityId, LocalDate from, LocalDate to, String reason) {
        CampusFacility facility = locked(facilityId);
        try {
            closures.saveAndFlush(new CampusFacilityClosure(facility, from, to, reason, now()));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The closure is not valid: " + invalid.getMessage() + ".");
        }
        return view(facility, LocalDate.now(clock.withZone(properties.zone())));
    }

    public BookingView book(Long accountId, Long facilityId, Instant startsAt, Instant endsAt, String purpose) {
        CampusFacility facility = locked(facilityId);
        if (!facility.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "This facility is not taking bookings.");
        }
        Instant now = now();
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt) || !startsAt.isAfter(now)
                || Duration.between(startsAt, endsAt).compareTo(Duration.ofHours(12)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a future time of at most 12 hours that ends after it starts.");
        }
        ZoneId zone = properties.zone();
        ZonedDateTime start = startsAt.atZone(zone);
        ZonedDateTime end = endsAt.atZone(zone);
        if (!start.toLocalDate().equals(end.toLocalDate()) || start.toLocalTime().isBefore(facility.getOpensAt())
                || end.toLocalTime().isAfter(facility.getClosesAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "The facility is open " + facility.getOpensAt() + " to " + facility.getClosesAt()
                    + " (" + zone.getId() + "). Book within one day's opening hours.");
        }
        if (closures.closedOn(facility.getId(), start.toLocalDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "The facility is closed on that day.");
        }
        boolean clash = bookings.findOverlapping(facility.getId(), startsAt, endsAt).stream()
                .map(this::statusOf).anyMatch(status -> status.equals("CONFIRMED") || status.equals("PENDING"));
        if (clash) {
            throw new ApiException(HttpStatus.CONFLICT, "Someone has already booked part of that time.");
        }
        CampusBooking booking;
        try {
            booking = bookings.saveAndFlush(new CampusBooking(facility, accounts.getReferenceById(accountId),
                    startsAt.truncatedTo(ChronoUnit.MICROS), endsAt.truncatedTo(ChronoUnit.MICROS), purpose, now));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The booking is not valid: " + invalid.getMessage() + ".");
        }
        if (facility.isNeedsApproval()) {
            booking.linkApproval(approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, accountId, SOURCE_TYPE,
                    String.valueOf(booking.getId()), "Booking of " + facility.getName())).approvalRequestId());
        }
        return viewOf(booking);
    }

    /** The booker cancels their own booking before it starts; a pending approval is cancelled with it. */
    public BookingView cancel(Long accountId, Long bookingId) {
        CampusBooking booking = bookings.findById(bookingId == null ? -1L : bookingId)
                .filter(found -> found.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!booking.getStartsAt().isAfter(now())) {
            throw new ApiException(HttpStatus.CONFLICT, "A booking that has started can no longer be cancelled.");
        }
        locked(booking.getFacility().getId());
        if (booking.getApprovalRequestId() != null) {
            approvals.cancelApprovalIfPending(booking.getApprovalRequestId(), accountId);
        }
        booking.cancel(now());
        return viewOf(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingView> mine(Long accountId) {
        return bookings.findAllForAccount(accountId, PageRequest.of(0, 50)).stream().map(this::viewOf).toList();
    }

    /** A facility's coming bookings, so people can see free times. Shows who booked by login ID. */
    @Transactional(readOnly = true)
    public List<BookingView> upcoming(Long facilityId) {
        return bookings.findUpcoming(facilityId, now(), PageRequest.of(0, 100)).stream().map(this::viewOf)
                .filter(b -> b.status().equals("CONFIRMED") || b.status().equals("PENDING")).toList();
    }

    private String statusOf(CampusBooking booking) {
        if (booking.getCancelledAt() != null) {
            return "CANCELLED";
        }
        if (booking.getApprovalRequestId() == null) {
            return "CONFIRMED";
        }
        ApprovalRequestStatus status = approvalQueries.requestStatus(booking.getApprovalRequestId())
                .map(ApprovalRequestOutcome::status).orElse(ApprovalRequestStatus.PENDING);
        return switch (status) {
            case APPROVED -> "CONFIRMED";
            case REJECTED -> "REJECTED";
            case CANCELLED -> "CANCELLED";
            default -> "PENDING";
        };
    }

    private BookingView viewOf(CampusBooking booking) {
        return new BookingView(booking.getId(), booking.getFacility().getId(), booking.getFacility().getName(),
                booking.getAccount().getLoginId(), booking.getStartsAt(), booking.getEndsAt(), booking.getPurpose(), statusOf(booking));
    }

    private FacilityView view(CampusFacility f, LocalDate today) {
        return new FacilityView(f.getId(), f.getCode(), f.getName(), f.getKind(), f.getLocation(), f.getCapacity(), f.getOpensAt(),
                f.getClosesAt(), f.isNeedsApproval(), f.isActive(),
                f.getId() == null ? List.of() : closures.findFrom(f.getId(), today).stream()
                        .map(c -> new ClosureView(c.getId(), c.getStartDate(), c.getEndDate(), c.getReason())).toList());
    }

    private CampusFacility locked(Long facilityId) {
        return facilities.findByIdForUpdate(facilityId == null ? -1L : facilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Facility not found."));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
