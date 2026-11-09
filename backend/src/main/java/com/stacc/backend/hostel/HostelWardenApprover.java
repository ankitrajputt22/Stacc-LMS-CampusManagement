package com.stacc.backend.hostel;

import java.util.Optional;

import com.stacc.backend.approval.ApproverRelationship;
import com.stacc.backend.approval.ApproverRelationshipResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The warden of the requesting student's current hostel: the student's ACTIVE allocation, then that hostel's active
 * warden for the room's block if there is one, otherwise for the whole hostel.
 */
@Component
public class HostelWardenApprover implements ApproverRelationshipResolver {

    private final HostelAllocationRepository allocations;
    private final HostelWardenRepository wardens;

    public HostelWardenApprover(HostelAllocationRepository allocations, HostelWardenRepository wardens) {
        this.allocations = allocations;
        this.wardens = wardens;
    }

    @Override
    public ApproverRelationship relationship() {
        return ApproverRelationship.STUDENT_HOSTEL_WARDEN;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> approverFor(Long requesterAccountId) {
        return allocations.findActiveForStudentAccount(requesterAccountId).flatMap(allocation -> {
            HostelRoom room = allocation.getBed().getRoom();
            return wardens.findActiveByHostel(room.getHostel().getId()).stream()
                    .filter(w -> w.getBlock() == null || w.getBlock().equalsIgnoreCase(room.getBlock() == null ? "" : room.getBlock()))
                    .findFirst()
                    .map(w -> w.getWarden().getId());
        });
    }
}
