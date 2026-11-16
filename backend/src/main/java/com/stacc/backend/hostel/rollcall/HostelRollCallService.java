package com.stacc.backend.hostel.rollcall;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.hostel.Hostel;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelWardenService;
import com.stacc.backend.hostel.outing.GateMovement;
import com.stacc.backend.hostel.outing.GateMovementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Night roll calls, for the wardens of a hostel only ({@link HostelWardenService#isWardenOf}); another hostel's sheets
 * answer the same 404 as missing ones. A sheet lists the residents allocated in the hostel (or block) when it is
 * opened, pre-filled from the gate log ("tonight" is the date in the college's time zone); marks change only while it is open, under its row lock, and finalizing needs
 * every resident marked.
 */
@Service
@Transactional
public class HostelRollCallService {

    static final String NOT_FOUND = "Roll call not found.";
    static final String HOSTEL_NOT_FOUND = "Hostel not found.";
    static final String EXISTS = "There is already a roll call for this hostel or block on that night.";
    static final String FINALIZED = "This roll call is finalized and no longer changes.";
    static final String UNMARKED_LEFT = "Mark every resident before finalizing.";

    public record Summary(Long rollCallId, Long hostelId, String hostelName, String block, LocalDate rollDate,
            HostelRollCall.Status status, long residents, long present, long absent, long out, long unmarked,
            Instant finalizedAt) {
    }

    public record EntryView(Long entryId, String studentLoginId, String room, String bed, HostelRollCallEntry.Status status,
            boolean prefilledOut) {
    }

    public record Detail(Summary summary, List<EntryView> entries) {
    }

    public record Mark(Long entryId, HostelRollCallEntry.Status status) {
    }

    private final HostelRollCallRepository rollCalls;
    private final HostelRollCallEntryRepository entries;
    private final HostelAllocationRepository allocations;
    private final HostelRepository hostels;
    private final GateMovementRepository movements;
    private final HostelWardenService wardens;
    private final UserAccountRepository accounts;
    private final EngagementProperties college;
    private final Clock clock;

    public HostelRollCallService(HostelRollCallRepository rollCalls, HostelRollCallEntryRepository entries,
            HostelAllocationRepository allocations, HostelRepository hostels, GateMovementRepository movements,
            HostelWardenService wardens, UserAccountRepository accounts, EngagementProperties college, Clock clock) {
        this.rollCalls = rollCalls;
        this.entries = entries;
        this.allocations = allocations;
        this.hostels = hostels;
        this.movements = movements;
        this.wardens = wardens;
        this.accounts = accounts;
        this.college = college;
        this.clock = clock;
    }

    /** Opens the night's sheet for the hostel, or one block of it, with the residents allocated there now. */
    public Detail open(Long wardenAccountId, Long hostelId, String block, LocalDate night) {
        if (hostelId == null || !wardens.isWardenOf(wardenAccountId, hostelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND);
        }
        Hostel hostel = hostels.findById(hostelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND));
        String wantedBlock = block == null || block.isBlank() ? null : block.strip();
        HostelRollCall rollCall;
        try {
            rollCall = rollCalls.saveAndFlush(new HostelRollCall(hostel, wantedBlock,
                    night == null ? LocalDate.now(clock.withZone(college.zone())) : night, accounts.getReferenceById(wardenAccountId), now()));
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, EXISTS);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        for (HostelAllocation allocation : allocations.findActiveInHostel(hostelId)) {
            if (wantedBlock == null || wantedBlock.equalsIgnoreCase(Objects.toString(allocation.getBed().getRoom().getBlock(), ""))) {
                boolean outside = movements.findFirstByStudentAccountIdOrderByRecordedAtDescIdDesc(
                        allocation.getHostelApplication().getStudentProfile().getUserAccount().getId())
                        .map(movement -> movement.getDirection() == GateMovement.Direction.OUT).orElse(false);
                entries.save(new HostelRollCallEntry(rollCall, allocation, outside));
            }
        }
        entries.flush();
        return detailOf(rollCall);
    }

    /** The hostel's recent sheets, newest night first. */
    @Transactional(readOnly = true)
    public List<Summary> recent(Long wardenAccountId, Long hostelId) {
        if (hostelId == null || !wardens.isWardenOf(wardenAccountId, hostelId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND);
        }
        List<HostelRollCall> found = rollCalls.findRecentForHostel(hostelId, PageRequest.of(0, 30));
        Map<Long, Map<HostelRollCallEntry.Status, Long>> counts = counts(found.stream().map(HostelRollCall::getId).toList());
        return found.stream().map(rollCall -> summary(rollCall, counts.getOrDefault(rollCall.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(Long wardenAccountId, Long rollCallId) {
        requireWarden(wardenAccountId, rollCallId);
        return detailOf(rollCalls.findWithHostelById(rollCallId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND)));
    }

    /** Marks residents of an open sheet; each entry must belong to it. */
    public Detail mark(Long wardenAccountId, Long rollCallId, List<Mark> marks) {
        HostelRollCall rollCall = lockedOpen(wardenAccountId, rollCallId);
        Map<Long, HostelRollCallEntry> sheet = entries.findAllForRollCall(rollCallId).stream()
                .collect(Collectors.toMap(HostelRollCallEntry::getId, Function.identity()));
        Instant now = now();
        for (Mark mark : marks) {
            HostelRollCallEntry entry = mark.entryId() == null ? null : sheet.get(mark.entryId());
            if (entry == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every mark must name a resident of this roll call.");
            }
            try {
                entry.mark(mark.status(), accounts.getReferenceById(wardenAccountId), now);
            } catch (IllegalArgumentException invalid) {
                throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
            }
        }
        entries.flush();
        return detailOf(rollCall);
    }

    /** Finalizes an open sheet with every resident marked; finalizing again changes nothing. */
    public Detail finalizeRollCall(Long wardenAccountId, Long rollCallId) {
        requireWarden(wardenAccountId, rollCallId);
        HostelRollCall rollCall = rollCalls.findByIdForUpdate(rollCallId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (rollCall.isOpen()) {
            if (entries.findAllForRollCall(rollCallId).stream().anyMatch(entry -> entry.getStatus() == HostelRollCallEntry.Status.UNMARKED)) {
                throw new ApiException(HttpStatus.CONFLICT, UNMARKED_LEFT);
            }
            rollCall.finalizeAt(now());
        }
        return detailOf(rollCall);
    }

    private HostelRollCall lockedOpen(Long wardenAccountId, Long rollCallId) {
        requireWarden(wardenAccountId, rollCallId);
        HostelRollCall rollCall = rollCalls.findByIdForUpdate(rollCallId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (!rollCall.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, FINALIZED);
        }
        return rollCall;
    }

    private void requireWarden(Long wardenAccountId, Long rollCallId) {
        Optional<Long> hostelId = rollCallId == null ? Optional.empty() : rollCalls.findHostelIdById(rollCallId);
        if (hostelId.isEmpty() || !wardens.isWardenOf(wardenAccountId, hostelId.get())) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
    }

    private Detail detailOf(HostelRollCall rollCall) {
        List<HostelRollCallEntry> sheet = entries.findAllForRollCall(rollCall.getId());
        Map<HostelRollCallEntry.Status, Long> counts = sheet.stream()
                .collect(Collectors.groupingBy(HostelRollCallEntry::getStatus, Collectors.counting()));
        return new Detail(summary(rollCall, counts), sheet.stream().map(entry -> new EntryView(entry.getId(),
                entry.getAllocation().getHostelApplication().getStudentProfile().getUserAccount().getLoginId(),
                entry.getAllocation().getBed().getRoom().getRoomNumber(), entry.getAllocation().getBed().getBedNumber(),
                entry.getStatus(), entry.isPrefilledOut())).toList());
    }

    private Map<Long, Map<HostelRollCallEntry.Status, Long>> counts(List<Long> rollCallIds) {
        Map<Long, Map<HostelRollCallEntry.Status, Long>> counts = new HashMap<>();
        if (!rollCallIds.isEmpty()) {
            for (HostelRollCallEntryRepository.StatusCount row : entries.countByStatus(rollCallIds)) {
                counts.computeIfAbsent(row.getRollCallId(), id -> new HashMap<>()).put(row.getStatus(), row.getResidents());
            }
        }
        return counts;
    }

    private static Summary summary(HostelRollCall rollCall, Map<HostelRollCallEntry.Status, Long> counts) {
        long present = counts.getOrDefault(HostelRollCallEntry.Status.PRESENT, 0L);
        long absent = counts.getOrDefault(HostelRollCallEntry.Status.ABSENT, 0L);
        long out = counts.getOrDefault(HostelRollCallEntry.Status.OUT, 0L);
        long unmarked = counts.getOrDefault(HostelRollCallEntry.Status.UNMARKED, 0L);
        return new Summary(rollCall.getId(), rollCall.getHostel().getId(), rollCall.getHostel().getName(), rollCall.getBlock(),
                rollCall.getRollDate(), rollCall.getStatus(), present + absent + out + unmarked, present, absent, out, unmarked,
                rollCall.getFinalizedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
