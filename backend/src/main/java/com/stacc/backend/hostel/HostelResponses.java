package com.stacc.backend.hostel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.approval.ApprovalCommands.CurrentApprovalStep;
import com.stacc.backend.approval.ApprovalResponses.ApprovalHistoryResponse;
import com.stacc.backend.approval.ApprovalResponses.ApprovalRequestStepResponse;
import com.stacc.backend.auth.account.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The hostel module as clients see it. No entity is sent as it is. Students never see internal IDs of allocations,
 * beds, or accounts; admins get the IDs they need to choose and act. People appear by login ID only.
 */
public final class HostelResponses {

    private HostelResponses() {
    }

    static String loginIdOf(UserAccount account) {
        return account == null ? null : account.getLoginId();
    }

    public record HostelResponse(
            @Schema(example = "1") Long hostelId,
            @Schema(example = "MAIN") String code,
            @Schema(example = "Main Hostel") String name,
            @Schema(nullable = true) String description,
            boolean active,
            @Schema(example = "2") long roomCount,
            @Schema(description = "Active beds, counted from the beds themselves.", example = "4") long activeBedCount,
            @Schema(description = "Beds with an active allocation.", example = "1") long occupiedBedCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record HostelRoomResponse(
            Long roomId,
            Long hostelId,
            @Schema(example = "A-101") String roomNumber,
            boolean active,
            @Schema(description = "The room's capacity: its active beds.", example = "2") long activeBedCount,
            @Schema(example = "1") long occupiedBedCount,
            @Schema(description = "True once a bed of the room was ever allocated: the room number can no longer change.")
            boolean numberLocked) {
    }

    public record HostelBedResponse(
            Long bedId,
            Long roomId,
            @Schema(example = "B1") String bedNumber,
            boolean active,
            @Schema(description = "Derived: OCCUPIED with an active allocation, INACTIVE when the bed, its room, or its hostel is inactive, otherwise AVAILABLE.",
                    allowableValues = {"AVAILABLE", "OCCUPIED", "INACTIVE"}) String occupancy,
            @Schema(description = "True once the bed was ever allocated: its number can no longer change.") boolean numberLocked) {
    }

    public record AvailableHostelBed(Long bedId, String bedNumber, Long roomId, String roomNumber, Long hostelId,
            String hostelCode, String hostelName) {

        static AvailableHostelBed of(HostelBed bed) {
            HostelRoom room = bed.getRoom();
            Hostel hostel = room.getHostel();
            return new AvailableHostelBed(bed.getId(), bed.getBedNumber(), room.getId(), room.getRoomNumber(), hostel.getId(),
                    hostel.getCode(), hostel.getName());
        }
    }

    public record EligibleHostelSession(
            Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            LocalDate startDate,
            LocalDate endDate,
            @Schema(allowableValues = {"PLANNED", "ACTIVE", "CLOSED"}) String sessionStatus,
            @Schema(description = "True when the student already has a pending or approved application for it.")
            boolean hasCurrentApplication) {
    }

    /** A student's view of one allocation: where, and since when. */
    public record StudentHostelAllocation(
            @Schema(example = "MAIN") String hostelCode,
            @Schema(example = "Main Hostel") String hostelName,
            @Schema(example = "A-101") String roomNumber,
            @Schema(example = "B1") String bedNumber,
            @Schema(allowableValues = {"ACTIVE", "VACATED"}) String status,
            Instant allocatedAt,
            @Schema(nullable = true) Instant vacatedAt) {

        static StudentHostelAllocation of(HostelAllocation allocation) {
            HostelBed bed = allocation.getBed();
            HostelRoom room = bed.getRoom();
            Hostel hostel = room.getHostel();
            return new StudentHostelAllocation(hostel.getCode(), hostel.getName(), room.getRoomNumber(), bed.getBedNumber(),
                    allocation.getStatus().name(), allocation.getAllocatedAt(), allocation.getVacatedAt());
        }
    }

    /** An allocation as admins see it, with the IDs needed to act on it. */
    public record HostelAllocationResponse(
            Long allocationId,
            Long applicationId,
            @Schema(example = "HST-000001") String applicationNumber,
            @Schema(example = "student1") String studentLoginId,
            @Schema(example = "2026-27") String academicSessionCode,
            Long hostelId,
            String hostelCode,
            String hostelName,
            Long roomId,
            String roomNumber,
            Long bedId,
            String bedNumber,
            @Schema(allowableValues = {"ACTIVE", "VACATED"}) String status,
            Instant allocatedAt,
            @Schema(example = "admin1") String allocatedByLoginId,
            @Schema(nullable = true) Instant vacatedAt,
            @Schema(nullable = true) String vacatedByLoginId) {

        static HostelAllocationResponse of(HostelAllocation allocation) {
            HostelApplication application = allocation.getHostelApplication();
            HostelBed bed = allocation.getBed();
            HostelRoom room = bed.getRoom();
            Hostel hostel = room.getHostel();
            return new HostelAllocationResponse(allocation.getId(), application.getId(), application.getApplicationNumber(),
                    loginIdOf(application.getStudentProfile().getUserAccount()), application.getAcademicSession().getCode(),
                    hostel.getId(), hostel.getCode(), hostel.getName(), room.getId(), room.getRoomNumber(), bed.getId(),
                    bed.getBedNumber(), allocation.getStatus().name(), allocation.getAllocatedAt(),
                    loginIdOf(allocation.getAllocatedBy()), allocation.getVacatedAt(), loginIdOf(allocation.getVacatedBy()));
        }
    }

    /** One of the student's own applications in a list. The approval status is read from the approval request. */
    public record HostelApplicationSummary(
            Long applicationId,
            @Schema(example = "HST-000001") String applicationNumber,
            Long academicSessionId,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            Instant submittedAt,
            boolean hasActiveAllocation) {
    }

    /** One of the student's own applications in full: approval progress and allocations, without internal IDs. */
    public record HostelApplicationDetail(
            Long applicationId,
            String applicationNumber,
            Long academicSessionId,
            String academicSessionCode,
            @Schema(nullable = true, description = "Plain text.") String note,
            Instant submittedAt,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            @Schema(description = "True while the approval is pending and there is no active allocation.") boolean canCancel,
            @Schema(description = "The approval steps, from the approval engine, in order.")
            List<ApprovalRequestStepResponse> approvalSteps,
            @Schema(nullable = true) StudentHostelAllocation activeAllocation,
            @Schema(description = "Earlier, vacated allocations, oldest first.") List<StudentHostelAllocation> pastAllocations) {
    }

    public record HostelAdminApplicationSummary(
            Long applicationId,
            String applicationNumber,
            String studentLoginId,
            Long academicSessionId,
            String academicSessionCode,
            @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) String approvalStatus,
            Instant submittedAt,
            @Schema(nullable = true) HostelAllocationResponse activeAllocation) {
    }

    public record HostelAdminApplicationDetail(
            Long applicationId,
            String applicationNumber,
            String studentLoginId,
            Long academicSessionId,
            String academicSessionCode,
            @Schema(nullable = true) String note,
            Instant submittedAt,
            String approvalStatus,
            List<ApprovalRequestStepResponse> approvalSteps,
            List<ApprovalHistoryResponse> approvalHistory,
            @Schema(nullable = true, description = "The step waiting for a decision, while the approval is pending.")
            CurrentApprovalStep currentApprovalStep,
            @Schema(description = "Whether the signed-in admin may decide that step through the approval task routes: the step's role, APPROVAL_TASK_DECIDE, and not the requester.")
            boolean canDecideCurrentStep,
            @Schema(description = "True when the approval is APPROVED and the application has no active allocation.")
            boolean canAllocate,
            @Schema(description = "Every allocation, oldest first.") List<HostelAllocationResponse> allocations) {
    }

    public record HostelApplicationPage(List<HostelAdminApplicationSummary> items, int page, int size, long totalElements,
            int totalPages) {
    }

    public record HostelAllocationPage(List<HostelAllocationResponse> items, int page, int size, long totalElements,
            int totalPages) {
    }
}
