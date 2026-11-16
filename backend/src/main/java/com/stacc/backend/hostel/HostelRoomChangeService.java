package com.stacc.backend.hostel;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.ApprovalStartResult;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Room changes of hostel residents (v0.10.3). A resident asks to move, or proposes a swap of beds with another resident
 * who must accept first; then the resident's hostel warden decides through the HOSTEL_ROOM_CHANGE workflow, and the
 * hostel office carries out an approved change: the move to a bed it chooses, or both beds swapped at once, through
 * {@link HostelAllocationService} and its lock order. Approval state always comes from the engine. Students never
 * choose a bed; the requester and partner always come from the access token and the partner's login ID.
 */
@Service
@Transactional
public class HostelRoomChangeService {

    public static final String WORKFLOW_CODE = "HOSTEL_ROOM_CHANGE";
    public static final String SOURCE_TYPE = "HOSTEL_ROOM_CHANGE";
    static final String NOT_RESIDENT = "Only a student with a hostel bed can ask for a room change.";
    static final String PARTNER_NOT_RESIDENT = "That student has no hostel bed, so a swap is not possible.";
    static final String ALREADY_OPEN = "There is already an open room change for this bed. Wait for it to finish or cancel it.";
    static final String NOT_FOUND = "Room change not found.";
    static final String NOT_APPROVED = "Only a room change the warden approved can be carried out.";
    static final String BED_REQUIRED = "Choose the bed to move the student to.";
    static final String GONE = "A bed in this change is no longer allocated, so the change cannot be carried out. Cancel it instead.";

    public record Side(String loginId, String hostel, String room, String bed) {

        static Side of(HostelAllocation allocation) {
            if (allocation == null) {
                return null;
            }
            HostelBed bed = allocation.getBed();
            return new Side(allocation.getHostelApplication().getStudentProfile().getUserAccount().getLoginId(),
                    bed.getRoom().getHostel().getName(), bed.getRoom().getRoomNumber(), bed.getBedNumber());
        }
    }

    public record RoomChangeView(Long changeId, HostelRoomChange.Kind kind, Side requester, Side partner,
            HostelRoomChange.PartnerResponse partnerResponse, String reason, String preference, HostelRoomChange.Status status,
            String approvalStatus, Instant createdAt, Instant completedAt, boolean mine, boolean canCancel, boolean canAnswer,
            boolean canComplete) {
    }

    private final HostelRoomChangeRepository changes;
    private final HostelAllocationRepository allocations;
    private final HostelAllocationService allocationService;
    private final StudentProfileRepository profiles;
    private final UserAccountRepository accounts;
    private final ApprovalWorkflowService approvals;
    private final ApprovalQueryService approvalQueries;
    private final Clock clock;

    public HostelRoomChangeService(HostelRoomChangeRepository changes, HostelAllocationRepository allocations,
            HostelAllocationService allocationService, StudentProfileRepository profiles, UserAccountRepository accounts,
            ApprovalWorkflowService approvals, ApprovalQueryService approvalQueries, Clock clock) {
        this.changes = changes;
        this.allocations = allocations;
        this.allocationService = allocationService;
        this.profiles = profiles;
        this.accounts = accounts;
        this.approvals = approvals;
        this.approvalQueries = approvalQueries;
        this.clock = clock;
    }

    // --- Residents

    /** Asks to move to another bed; the warden decides, then the hostel office chooses the bed. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomChangeView requestMove(Long accountId, String reason, String preference) {
        lockResidents(accountId, null);
        HostelAllocation allocation = activeAllocation(accountId, NOT_RESIDENT);
        refuseIfOpen(allocation, null);
        HostelRoomChange change = changes.saveAndFlush(valid(() -> HostelRoomChange.move(allocation, reason, preference, now())));
        start(change, accountId, "Room move request");
        return view(change, accountId);
    }

    /** Proposes a swap of beds with the resident whose college ID is given; nothing goes to the warden until they accept. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomChangeView proposeSwap(Long accountId, String partnerLoginId, String reason) {
        UserAccount partner = (partnerLoginId == null ? Optional.<UserAccount>empty() : accounts.findByLoginId(partnerLoginId.strip()))
                .filter(found -> !found.getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, PARTNER_NOT_RESIDENT));
        lockResidents(accountId, partner.getId());
        HostelAllocation mine = activeAllocation(accountId, NOT_RESIDENT);
        HostelAllocation theirs = activeAllocation(partner.getId(), PARTNER_NOT_RESIDENT);
        refuseIfOpen(mine, theirs);
        HostelRoomChange change = changes.saveAndFlush(valid(() -> HostelRoomChange.swap(mine, theirs, reason, now())));
        return view(change, accountId);
    }

    /** The partner accepts or declines a proposed swap; accepting sends it to the requester's warden. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomChangeView answerSwap(Long accountId, Long changeId, boolean accept) {
        HostelRoomChange change = locked(changeId);
        HostelAllocation partner = change.getPartnerAllocation();
        if (partner == null || !partner.getHostelApplication().getStudentProfile().getUserAccount().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        valid(() -> {
            change.answer(accept);
            return change;
        });
        if (accept) {
            if (!change.getRequesterAllocation().isActive() || !partner.isActive()) {
                throw new ApiException(HttpStatus.CONFLICT, GONE);
            }
            start(change, accountOf(change.getRequesterAllocation()), "Room swap request");
        }
        return view(change, accountId);
    }

    /** The requester cancels an open change while it waits for the partner or for the warden. */
    public RoomChangeView cancel(Long accountId, Long changeId) {
        HostelRoomChange change = locked(changeId);
        if (!accountOf(change.getRequesterAllocation()).equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        if (change.getStatus() != HostelRoomChange.Status.OPEN
                || (change.getApprovalRequestId() != null && !approvals.cancelApprovalIfPending(change.getApprovalRequestId(), accountId))) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a room change still waiting for an answer or a decision can be cancelled.");
        }
        change.cancel();
        return view(change, accountId);
    }

    @Transactional(readOnly = true)
    public List<RoomChangeView> mine(Long accountId) {
        return changes.findAllForAccount(accountId).stream().map(change -> view(change, accountId)).toList();
    }

    // --- The hostel office

    @Transactional(readOnly = true)
    public List<RoomChangeView> open() {
        return changes.findAllInStatuses(EnumSet.of(HostelRoomChange.Status.OPEN)).stream().map(change -> view(change, null)).toList();
    }

    /**
     * Carries out an approved change: a move vacates the student's bed and allocates the chosen one; a swap vacates both
     * beds and allocates each student the other's. Everything happens in one transaction, so either all of it holds or
     * nothing changes.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomChangeView complete(Long adminAccountId, Long changeId, Long bedId) {
        HostelRoomChange change = locked(changeId);
        if (change.getStatus() != HostelRoomChange.Status.OPEN || !"APPROVED".equals(approvalStatus(change))) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_APPROVED);
        }
        HostelAllocation requester = allocations.findByIdForUpdate(change.getRequesterAllocation().getId())
                .filter(HostelAllocation::isActive).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, GONE));
        if (change.getKind() == HostelRoomChange.Kind.MOVE) {
            if (bedId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, BED_REQUIRED);
            }
            allocationService.vacate(adminAccountId, requester.getId());
            allocationService.allocate(adminAccountId, requester.getHostelApplication().getId(), bedId);
        } else {
            HostelAllocation partner = allocations.findByIdForUpdate(change.getPartnerAllocation().getId())
                    .filter(HostelAllocation::isActive).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, GONE));
            Long requesterBed = requester.getBed().getId();
            Long partnerBed = partner.getBed().getId();
            allocationService.vacate(adminAccountId, requester.getId());
            allocationService.vacate(adminAccountId, partner.getId());
            allocationService.allocate(adminAccountId, requester.getHostelApplication().getId(), partnerBed);
            allocationService.allocate(adminAccountId, partner.getHostelApplication().getId(), requesterBed);
        }
        change.complete(accounts.getReferenceById(adminAccountId), now());
        changes.saveAndFlush(change);
        return view(change, null);
    }

    // --- Helpers

    // Locks the residents' student rows, the lower account ID first, so a swap and another change of either student
    // take turns and two swaps proposed between the same students at once never wait on each other in a circle.
    private void lockResidents(Long accountId, Long partnerAccountId) {
        if (partnerAccountId != null && partnerAccountId < accountId) {
            lockResident(partnerAccountId, PARTNER_NOT_RESIDENT, HttpStatus.CONFLICT);
            lockResident(accountId, NOT_RESIDENT, HttpStatus.FORBIDDEN);
        } else {
            lockResident(accountId, NOT_RESIDENT, HttpStatus.FORBIDDEN);
            if (partnerAccountId != null) {
                lockResident(partnerAccountId, PARTNER_NOT_RESIDENT, HttpStatus.CONFLICT);
            }
        }
    }

    private void lockResident(Long accountId, String refusal, HttpStatus status) {
        profiles.findByUserAccountIdForUpdate(accountId)
                .filter(found -> found.getStatus() == StudentProfileStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(status, refusal));
    }

    private HostelAllocation activeAllocation(Long accountId, String refusal) {
        return allocations.findActiveForStudentAccount(accountId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, refusal));
    }

    private void refuseIfOpen(HostelAllocation first, HostelAllocation second) {
        List<Long> ids = second == null ? List.of(first.getId()) : List.of(first.getId(), second.getId());
        if (changes.existsOpenFor(ids)) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_OPEN);
        }
    }

    private void start(HostelRoomChange change, Long requesterAccountId, String subject) {
        ApprovalStartResult started = approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, requesterAccountId, SOURCE_TYPE,
                String.valueOf(change.getId()), subject));
        change.linkApproval(started.approvalRequestId());
    }

    private HostelRoomChange locked(Long changeId) {
        return (changeId == null ? Optional.<HostelRoomChange>empty() : changes.findByIdForUpdate(changeId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static Long accountOf(HostelAllocation allocation) {
        return allocation.getHostelApplication().getStudentProfile().getUserAccount().getId();
    }

    private String approvalStatus(HostelRoomChange change) {
        return change.getApprovalRequestId() == null ? null : approvalQueries.requestStatus(change.getApprovalRequestId())
                .map(ApprovalRequestOutcome::status).map(ApprovalRequestStatus::name).orElse(null);
    }

    RoomChangeView view(HostelRoomChange change, Long viewerAccountId) {
        String approval = approvalStatus(change);
        boolean requester = viewerAccountId != null && accountOf(change.getRequesterAllocation()).equals(viewerAccountId);
        boolean partner = viewerAccountId != null && change.getPartnerAllocation() != null
                && accountOf(change.getPartnerAllocation()).equals(viewerAccountId);
        boolean open = change.getStatus() == HostelRoomChange.Status.OPEN;
        return new RoomChangeView(change.getId(), change.getKind(), Side.of(change.getRequesterAllocation()),
                Side.of(change.getPartnerAllocation()), change.getPartnerResponse(), change.getReason(), change.getPreference(),
                change.getStatus(), approval, change.getCreatedAt(), change.getCompletedAt(), requester,
                requester && open && (approval == null || "PENDING".equals(approval)),
                partner && open && change.getPartnerResponse() == HostelRoomChange.PartnerResponse.PENDING,
                viewerAccountId == null && open && "APPROVED".equals(approval));
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
