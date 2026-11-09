package com.stacc.backend.hostel.outing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.pass.Pass;
import com.stacc.backend.pass.PassPurpose;
import com.stacc.backend.pass.PassService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hostel outings: a resident asks for a gate pass or leave (and extensions of leave), approved by their hostel's warden
 * through the HOSTEL_OUTING workflow. Approval state always comes from the engine. An approved outing shows its pass as a
 * fresh code each time it is opened (earlier codes stop working). Security checks a code and records the student going
 * out and coming back; whether a student is outside comes from the latest movement. Overdue means approved, past its
 * end, and the student still outside, worked out on read.
 */
@Service
@Transactional
public class HostelOutingService {

    public static final String WORKFLOW_CODE = "HOSTEL_OUTING";
    public static final String SOURCE_TYPE = "HOSTEL_OUTING";
    public static final String EXTENSION_SOURCE_TYPE = "HOSTEL_OUTING_EXTENSION";
    static final String NOT_RESIDENT = "Only students who currently live in a hostel can ask to go out.";
    static final String UNAVAILABLE = "Outing not found.";

    public record ExtensionView(Long extensionId, Instant requestedUntil, String reason, String approvalStatus, Instant createdAt) {
    }

    public record OutingView(Long outingId, String studentLoginId, OutingKind kind, Instant leaveFrom, Instant leaveUntil,
            Instant originalUntil, String reason, String destination, String approvalStatus, Instant returnedAt, boolean outside,
            boolean overdue, boolean canCancel, boolean canExtend, boolean canShowPass, List<ExtensionView> extensions) {
    }

    /** The pass to show: its code (shown only now) and window. */
    public record PassView(String code, Instant validFrom, Instant validUntil) {
    }

    /** What security sees for a code: whether it lets the student out now, and why not. */
    public record PassCheck(boolean valid, String message, String studentLoginId, String hostel, String room, OutingKind kind,
            Instant leaveFrom, Instant leaveUntil, boolean outside, Long outingId) {
    }

    public record MovementView(Long movementId, String studentLoginId, GateMovement.Direction direction, Instant recordedAt,
            String recordedByLoginId) {
    }

    private final HostelOutingRepository outings;
    private final HostelOutingExtensionRepository extensions;
    private final GateMovementRepository movements;
    private final StudentProfileRepository profiles;
    private final HostelAllocationRepository allocations;
    private final UserAccountRepository accounts;
    private final ApprovalWorkflowService approvals;
    private final ApprovalQueryService approvalQueries;
    private final PassService passes;
    private final Clock clock;

    public HostelOutingService(HostelOutingRepository outings, HostelOutingExtensionRepository extensions,
            GateMovementRepository movements, StudentProfileRepository profiles, HostelAllocationRepository allocations,
            UserAccountRepository accounts, ApprovalWorkflowService approvals, ApprovalQueryService approvalQueries,
            PassService passes, Clock clock) {
        this.outings = outings;
        this.extensions = extensions;
        this.movements = movements;
        this.profiles = profiles;
        this.allocations = allocations;
        this.accounts = accounts;
        this.approvals = approvals;
        this.approvalQueries = approvalQueries;
        this.passes = passes;
        this.clock = clock;
    }

    // --- Students

    public OutingView request(Long accountId, OutingKind kind, Instant from, Instant until, String reason, String destination) {
        StudentProfile profile = profiles.findByUserAccountIdForUpdate(accountId)
                .filter(found -> found.getStatus() == StudentProfileStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, NOT_RESIDENT));
        if (allocations.findActiveForStudentAccount(accountId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_RESIDENT);
        }
        Instant now = now();
        if (from == null || until == null || from.isBefore(now.minus(Duration.ofMinutes(10)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose when you leave, from now on, and when you return.");
        }
        HostelOuting outing;
        try {
            outing = outings.saveAndFlush(new HostelOuting(profile, kind, from, until, reason, destination, now));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The request is not valid: " + invalid.getMessage() + ".");
        }
        ApprovalStartResult started = approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, accountId, SOURCE_TYPE,
                String.valueOf(outing.getId()), (kind == OutingKind.GATE_PASS ? "Gate pass" : "Leave") + " request"));
        outing.linkApproval(started.approvalRequestId());
        return view(outing, now);
    }

    @Transactional(readOnly = true)
    public List<OutingView> mine(Long accountId) {
        Instant now = now();
        return outings.findAllForStudentAccount(accountId, PageRequest.of(0, 50)).stream().map(outing -> view(outing, now)).toList();
    }

    /** Cancels the student's own outing while its approval is pending. */
    public OutingView cancel(Long accountId, Long outingId) {
        HostelOuting outing = own(accountId, outingId);
        if (outing.getApprovalRequestId() == null || !approvals.cancelApprovalIfPending(outing.getApprovalRequestId(), accountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a request waiting for approval can be cancelled.");
        }
        return view(outing, now());
    }

    /** Asks to stay away longer on approved leave that has not ended in a return; one pending extension at a time. */
    public OutingView extend(Long accountId, Long outingId, Instant until, String reason) {
        HostelOuting outing = own(accountId, outingId);
        Instant now = now();
        OutingView current = view(outing, now);
        if (!current.canExtend()) {
            throw new ApiException(HttpStatus.CONFLICT, "Only approved leave you have not returned from can be extended, one request at a time.");
        }
        if (until == null || !until.isAfter(current.leaveUntil()) || Duration.between(outing.getLeaveFrom(), until).compareTo(Duration.ofDays(120)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a later return, at most 120 days after you left.");
        }
        HostelOutingExtension extension;
        try {
            extension = extensions.saveAndFlush(new HostelOutingExtension(outing, until, reason, now));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The request is not valid: " + invalid.getMessage() + ".");
        }
        ApprovalStartResult started = approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, accountId,
                EXTENSION_SOURCE_TYPE, String.valueOf(extension.getId()), "Leave extension request"));
        extension.linkApproval(started.approvalRequestId());
        return view(outing, now);
    }

    /** A fresh pass code for the student's approved outing; earlier codes for it stop working. */
    public PassView pass(Long accountId, Long outingId) {
        HostelOuting outing = own(accountId, outingId);
        Instant now = now();
        OutingView current = view(outing, now);
        if (!current.canShowPass()) {
            throw new ApiException(HttpStatus.CONFLICT, "This outing has no pass now: it is not approved, has ended, or you are back.");
        }
        PassService.IssuedPass issued = passes.issue(PassPurpose.HOSTEL_OUTING, outing.getId(), accountId,
                outing.getLeaveFrom().minus(Duration.ofHours(1)), current.leaveUntil().plus(Duration.ofHours(12)));
        return new PassView(issued.code(), issued.pass().getValidFrom(), issued.pass().getValidUntil());
    }

    // --- Security

    @Transactional(readOnly = true)
    public PassCheck check(String code) {
        Instant now = now();
        Optional<Pass> found = passes.find(code).filter(pass -> pass.getPurpose() == PassPurpose.HOSTEL_OUTING);
        if (found.isEmpty()) {
            return new PassCheck(false, "No pass has this code.", null, null, null, null, null, null, false, null);
        }
        Pass pass = found.get();
        HostelOuting outing = outings.findById(pass.getSourceId()).orElse(null);
        if (outing == null) {
            return new PassCheck(false, "No pass has this code.", null, null, null, null, null, null, false, null);
        }
        OutingView view = view(outing, now);
        String location = null;
        String room = null;
        Optional<HostelAllocation> allocation = allocations.findActiveForStudentAccount(pass.getHolderAccountId());
        if (allocation.isPresent()) {
            location = allocation.get().getBed().getRoom().getHostel().getName();
            room = allocation.get().getBed().getRoom().getRoomNumber();
        }
        String message = pass.getRevokedAt() != null ? "This code was replaced by a newer one or withdrawn."
                : !pass.validAt(now) ? "This pass is not valid at this time."
                : !"APPROVED".equals(view.approvalStatus()) ? "This outing is not approved."
                : view.returnedAt() != null ? "The student has already come back from this outing."
                : view.outside() ? "Valid. The student is outside: record the return." : "Valid. Record the student going out.";
        boolean valid = message.startsWith("Valid");
        return new PassCheck(valid, message, view.studentLoginId(), location, room, outing.getKind(), outing.getLeaveFrom(),
                view.leaveUntil(), view.outside(), outing.getId());
    }

    /**
     * Records the student crossing the gate with this code: OUT needs a valid pass; IN is taken from a student who is
     * outside on this outing even after it ended, and marks the outing returned.
     */
    public PassCheck record(Long securityAccountId, String code, GateMovement.Direction direction) {
        PassCheck check = check(code);
        if (check.outingId() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No pass has this code.");
        }
        HostelOuting outing = outings.findByIdForUpdate(check.outingId()).orElseThrow();
        Long studentId = outing.getStudentProfile().getUserAccount().getId();
        boolean outside = outside(studentId);
        Instant now = now();
        if (direction == GateMovement.Direction.OUT) {
            if (!check.valid() || outside) {
                throw new ApiException(HttpStatus.CONFLICT, outside ? "The student is already outside." : check.message());
            }
        } else {
            if (!outside || outing.getReturnedAt() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "The student is not outside on this outing.");
            }
            outing.returned(now);
            passes.revokeAll(PassPurpose.HOSTEL_OUTING, outing.getId());
        }
        movements.saveAndFlush(new GateMovement(studentId, outing.getId(), direction, now, securityAccountId));
        return check(code);
    }

    @Transactional(readOnly = true)
    public List<MovementView> recentMovements() {
        List<GateMovement> recent = movements.findRecent(PageRequest.of(0, 50));
        Map<Long, String> logins = accounts.findAllById(recent.stream().flatMap(m -> java.util.stream.Stream.of(m.getStudentAccountId(), m.getRecordedBy()))
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(a -> a.getId(), a -> a.getLoginId()));
        return recent.stream().map(m -> new MovementView(m.getId(), logins.get(m.getStudentAccountId()), m.getDirection(),
                m.getRecordedAt(), logins.get(m.getRecordedBy()))).toList();
    }

    /** Manual lookup by login ID: the student's outings, newest first, with whether they are outside. */
    @Transactional(readOnly = true)
    public List<OutingView> lookup(String loginId) {
        Long accountId = accounts.findByLoginId(loginId == null ? "" : loginId.strip())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student has this login ID.")).getId();
        Instant now = now();
        return outings.findAllForStudentAccount(accountId, PageRequest.of(0, 10)).stream().map(outing -> view(outing, now)).toList();
    }

    // --- Wardens

    /** Approved outings of these students that are past their end while the student is still outside. */
    @Transactional(readOnly = true)
    public List<OutingView> overdue(List<Long> studentAccountIds) {
        if (studentAccountIds.isEmpty()) {
            return List.of();
        }
        Instant now = now();
        return outings.findOpenForStudents(studentAccountIds).stream().map(outing -> view(outing, now))
                .filter(OutingView::overdue).sorted(Comparator.comparing(OutingView::leaveUntil)).toList();
    }

    // --- Shared

    private HostelOuting own(Long accountId, Long outingId) {
        return outings.findByIdForUpdate(outingId == null ? -1L : outingId)
                .filter(outing -> outing.getStudentProfile().getUserAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, UNAVAILABLE));
    }

    private boolean outside(Long studentAccountId) {
        return movements.findFirstByStudentAccountIdOrderByRecordedAtDescIdDesc(studentAccountId)
                .map(movement -> movement.getDirection() == GateMovement.Direction.OUT).orElse(false);
    }

    private String statusOf(Long approvalRequestId) {
        return approvalRequestId == null ? "PENDING" : approvalQueries.requestStatus(approvalRequestId)
                .map(ApprovalRequestOutcome::status).map(ApprovalRequestStatus::name).orElse("PENDING");
    }

    OutingView view(HostelOuting outing, Instant now) {
        String status = statusOf(outing.getApprovalRequestId());
        List<ExtensionView> extensionViews = extensions.findAllForOutings(List.of(outing.getId())).stream()
                .map(e -> new ExtensionView(e.getId(), e.getRequestedUntil(), e.getReason(), statusOf(e.getApprovalRequestId()), e.getCreatedAt()))
                .toList();
        // The end is the latest approved extension's, otherwise the original end.
        Instant until = extensionViews.stream().filter(e -> "APPROVED".equals(e.approvalStatus())).map(ExtensionView::requestedUntil)
                .max(Comparator.naturalOrder()).orElse(outing.getLeaveUntil());
        Long studentId = outing.getStudentProfile().getUserAccount().getId();
        boolean outside = outing.getReturnedAt() == null && movements.findFirstByStudentAccountIdOrderByRecordedAtDescIdDesc(studentId)
                .filter(m -> m.getDirection() == GateMovement.Direction.OUT && outing.getId().equals(m.getOutingId())).isPresent();
        boolean approved = "APPROVED".equals(status);
        boolean pendingExtension = extensionViews.stream().anyMatch(e -> "PENDING".equals(e.approvalStatus()));
        return new OutingView(outing.getId(), outing.getStudentProfile().getUserAccount().getLoginId(), outing.getKind(),
                outing.getLeaveFrom(), until, outing.getLeaveUntil(), outing.getReason(), outing.getDestination(), status,
                outing.getReturnedAt(), outside, approved && outside && now.isAfter(until), "PENDING".equals(status),
                approved && outing.getKind() == OutingKind.LEAVE && outing.getReturnedAt() == null && !pendingExtension,
                approved && outing.getReturnedAt() == null && now.isBefore(until.plus(Duration.ofHours(12))), extensionViews);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
