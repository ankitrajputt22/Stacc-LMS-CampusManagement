package com.stacc.backend.hostel;

import java.time.Clock;
import java.util.List;

import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelBedRepository.BedLocation;
import com.stacc.backend.hostel.HostelResponses.AvailableHostelBed;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocating approved students to beds, and vacating. At any moment a bed, an application, and a student each have at
 * most one ACTIVE allocation. MySQL has no partial unique keys for that, so every allocation takes its locks in one
 * fixed order (student, application, hostel and room shared, bed) and checks under them; of two allocations racing for
 * a bed, exactly one wins and the other gets 409. The admin always comes from the access token.
 */
@Service
@Transactional
public class HostelAllocationService {

    static final String APPLICATION_NOT_FOUND = HostelApplicationService.NOT_FOUND;
    static final String BED_NOT_FOUND = "Bed not found.";
    static final String ALLOCATION_NOT_FOUND = "Hostel allocation not found.";
    static final String NOT_APPROVED = "Only an approved hostel application can be allocated a bed.";
    static final String APPLICATION_ALLOCATED = "This application already has an active allocation.";
    static final String STUDENT_ALLOCATED = "This student already has an active hostel allocation.";
    static final String BED_INACTIVE = "This bed cannot be allocated: the bed, its room, or its hostel is inactive.";
    static final String BED_TAKEN = "This bed is no longer available.";

    private final HostelApplicationRepository applications;
    private final HostelAllocationRepository allocations;
    private final HostelRepository hostels;
    private final HostelRoomRepository rooms;
    private final HostelBedRepository beds;
    private final StudentProfileRepository studentProfiles;
    private final UserAccountRepository accounts;
    private final HostelNotifications notifications;
    private final Clock clock;

    public HostelAllocationService(HostelApplicationRepository applications, HostelAllocationRepository allocations,
            HostelRepository hostels, HostelRoomRepository rooms, HostelBedRepository beds,
            StudentProfileRepository studentProfiles, UserAccountRepository accounts, HostelNotifications notifications,
            Clock clock) {
        this.applications = applications;
        this.allocations = allocations;
        this.hostels = hostels;
        this.rooms = rooms;
        this.beds = beds;
        this.studentProfiles = studentProfiles;
        this.accounts = accounts;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Beds a new allocation can use, narrowed to a hostel or room when given. */
    @Transactional(readOnly = true)
    public List<AvailableHostelBed> availableBeds(Long hostelId, Long roomId) {
        return beds.findAvailable(hostelId, roomId).stream().map(AvailableHostelBed::of).toList();
    }

    /**
     * Allocates the bed to an approved application. Runs at READ COMMITTED, so every check after a lock sees what the
     * transaction that held it committed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HostelAllocationResponse allocate(Long adminAccountId, Long applicationId, Long bedId) {
        Long studentProfileId = (applicationId == null ? null
                : applications.findStudentProfileIdById(applicationId).orElse(null));
        if (studentProfileId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND);
        }
        // 1. The student: two allocations for the same student take turns.
        studentProfiles.findByIdForUpdate(studentProfileId);
        // 2. The application.
        HostelApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, APPLICATION_NOT_FOUND));
        if (application.approvalStatus() != ApprovalRequestStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_APPROVED);
        }
        if (allocations.existsActiveForApplication(applicationId)) {
            throw new ApiException(HttpStatus.CONFLICT, APPLICATION_ALLOCATED);
        }
        if (allocations.existsActiveForStudent(studentProfileId)) {
            throw new ApiException(HttpStatus.CONFLICT, STUDENT_ALLOCATED);
        }
        BedLocation location = (bedId == null ? null : beds.findLocationById(bedId).orElse(null));
        if (location == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, BED_NOT_FOUND);
        }
        // 3. The hostel and room, shared: a deactivation waits for this allocation, other allocations do not.
        hostels.findByIdForShare(location.getHostelId());
        rooms.findByIdForShare(location.getRoomId());
        // 4. The bed: two allocations of the same bed take turns.
        HostelBed bed = beds.findByIdForUpdate(bedId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, BED_NOT_FOUND));
        if (!bed.isAllocatable()) {
            throw new ApiException(HttpStatus.CONFLICT, BED_INACTIVE);
        }
        if (allocations.existsActiveForBed(bedId)) {
            throw new ApiException(HttpStatus.CONFLICT, BED_TAKEN);
        }
        HostelAllocation allocation = allocations.saveAndFlush(new HostelAllocation(application, bed,
                accounts.getReferenceById(adminAccountId), clock.instant()));
        notifications.allocated(allocation);
        return HostelAllocationResponse.of(allocations.findWithContextById(allocation.getId()).orElseThrow());
    }

    /** Ends an ACTIVE allocation, keeping it as history; the bed is available again. Vacating again changes nothing. */
    public HostelAllocationResponse vacate(Long adminAccountId, Long allocationId) {
        HostelAllocation allocation = (allocationId == null ? null : allocations.findByIdForUpdate(allocationId).orElse(null));
        if (allocation == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ALLOCATION_NOT_FOUND);
        }
        if (allocation.isActive()) {
            allocation.vacate(accounts.getReferenceById(adminAccountId), clock.instant());
            allocations.saveAndFlush(allocation);
            notifications.vacated(allocation);
        }
        return HostelAllocationResponse.of(allocations.findWithContextById(allocationId).orElseThrow());
    }
}
