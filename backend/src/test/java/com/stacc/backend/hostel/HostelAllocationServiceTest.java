package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalRequest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelResponses.HostelAllocationResponse;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class HostelAllocationServiceTest {

    private final HostelApplicationRepository applications = mock(HostelApplicationRepository.class);
    private final HostelAllocationRepository allocations = mock(HostelAllocationRepository.class);
    private final HostelRepository hostels = mock(HostelRepository.class);
    private final HostelRoomRepository rooms = mock(HostelRoomRepository.class);
    private final HostelBedRepository beds = mock(HostelBedRepository.class);
    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final HostelNotifications notifications = mock(HostelNotifications.class);
    private final HostelAllocationService service = new HostelAllocationService(applications, allocations, hostels, rooms,
            beds, studentProfiles, accounts, notifications, Clock.fixed(HostelFixtures.NOW, ZoneOffset.UTC));

    private final UserAccount studentAccount = HostelFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = HostelFixtures.account(1, "admin1", RoleName.ADMIN);
    private final StudentProfile student = HostelFixtures.student(5, studentAccount);
    private final Hostel hostel = HostelFixtures.hostel(1, "MAIN");
    private final HostelRoom room = HostelFixtures.room(2, hostel, "A-101");
    private final HostelBed bed = HostelFixtures.bed(3, room, "B1");
    private HostelApplication application;

    @BeforeEach
    void setUp() {
        application = withApproval(HostelFixtures.approved(12, studentAccount, admin));
        when(applications.findStudentProfileIdById(42L)).thenReturn(Optional.of(5L));
        when(studentProfiles.findByIdForUpdate(5L)).thenReturn(Optional.of(student));
        when(beds.findLocationById(3L)).thenReturn(Optional.of(location()));
        when(hostels.findByIdForShare(1L)).thenReturn(Optional.of(hostel));
        when(rooms.findByIdForShare(2L)).thenReturn(Optional.of(room));
        when(beds.findByIdForUpdate(3L)).thenReturn(Optional.of(bed));
        when(accounts.getReferenceById(1L)).thenReturn(admin);
        when(allocations.saveAndFlush(any())).thenAnswer(call -> {
            HostelAllocation saved = call.getArgument(0);
            if (saved.getId() == null) {
                HostelFixtures.withId(saved, 77);
            }
            when(allocations.findWithContextById(77L)).thenReturn(Optional.of(saved));
            return saved;
        });
    }

    private HostelApplication withApproval(ApprovalRequest approval) {
        HostelApplication stored = HostelFixtures.application(42, student, HostelFixtures.session(2, "2026-27", 2026), approval);
        when(applications.findByIdForUpdate(42L)).thenReturn(Optional.of(stored));
        return stored;
    }

    private static HostelBedRepository.BedLocation location() {
        return new HostelBedRepository.BedLocation() {
            public Long getRoomId() {
                return 2L;
            }

            public Long getHostelId() {
                return 1L;
            }
        };
    }

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void anApprovedApplicationGetsAnActiveAllocationByTheTokensAdminAndTheStudentIsTold() {
        HostelAllocationResponse result = service.allocate(1L, 42L, 3L);

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.allocatedByLoginId()).isEqualTo("admin1");
        assertThat(result.allocatedAt()).isNotNull();
        assertThat(result.studentLoginId()).isEqualTo("student1");
        assertThat(result.bedNumber()).isEqualTo("B1");
        ArgumentCaptor<HostelAllocation> saved = ArgumentCaptor.forClass(HostelAllocation.class);
        verify(allocations).saveAndFlush(saved.capture());
        verify(notifications).allocated(saved.getValue());
    }

    @Test
    void theLocksAreTakenInOneFixedOrderBeforeTheChecks() {
        service.allocate(1L, 42L, 3L);

        InOrder order = inOrder(studentProfiles, applications, hostels, rooms, beds, allocations);
        order.verify(studentProfiles).findByIdForUpdate(5L);
        order.verify(applications).findByIdForUpdate(42L);
        order.verify(hostels).findByIdForShare(1L);
        order.verify(rooms).findByIdForShare(2L);
        order.verify(beds).findByIdForUpdate(3L);
        order.verify(allocations).existsActiveForBed(3L);
        order.verify(allocations).saveAndFlush(any());
    }

    @Test
    void onlyApprovedApplicationsCanBeAllocated() {
        withApproval(HostelFixtures.approval(13, studentAccount));
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.NOT_APPROVED);

        ApprovalRequest rejected = HostelFixtures.approval(14, studentAccount);
        rejected.reject(rejected.currentStep().orElseThrow(), admin, "No.", HostelFixtures.NOW);
        withApproval(rejected);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.NOT_APPROVED);

        ApprovalRequest cancelled = HostelFixtures.approval(15, studentAccount);
        cancelled.cancel(HostelFixtures.NOW);
        withApproval(cancelled);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.NOT_APPROVED);
        verify(allocations, never()).saveAndFlush(any());
    }

    @Test
    void anOccupiedBedOrAnAlreadyAllocatedStudentOrApplicationIsAConflict() {
        when(allocations.existsActiveForBed(3L)).thenReturn(true);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.BED_TAKEN);

        when(allocations.existsActiveForStudent(5L)).thenReturn(true);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.STUDENT_ALLOCATED);

        when(allocations.existsActiveForApplication(42L)).thenReturn(true);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.APPLICATION_ALLOCATED);
        verify(allocations, never()).saveAndFlush(any());
    }

    @Test
    void inactiveBedsRoomsAndHostelsTakeNoAllocation() {
        hostel.update(hostel.getName(), null, false);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.BED_INACTIVE);
        hostel.update(hostel.getName(), null, true);
        room.update("A-101", false);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.BED_INACTIVE);
        room.update("A-101", true);
        bed.update("B1", false);
        assertRefused(() -> service.allocate(1L, 42L, 3L), HttpStatus.CONFLICT, HostelAllocationService.BED_INACTIVE);
    }

    @Test
    void unknownApplicationsAndBedsAre404() {
        assertRefused(() -> service.allocate(1L, 404L, 3L), HttpStatus.NOT_FOUND, HostelAllocationService.APPLICATION_NOT_FOUND);
        assertRefused(() -> service.allocate(1L, 42L, 404L), HttpStatus.NOT_FOUND, HostelAllocationService.BED_NOT_FOUND);
        assertRefused(() -> service.vacate(1L, 404L), HttpStatus.NOT_FOUND, HostelAllocationService.ALLOCATION_NOT_FOUND);
    }

    @Test
    void vacatingKeepsHistoryAndIsToldOnlyOnce() {
        HostelAllocation active = HostelFixtures.withId(new HostelAllocation(application, bed, admin, HostelFixtures.NOW), 77);
        when(allocations.findByIdForUpdate(77L)).thenReturn(Optional.of(active));
        when(allocations.findWithContextById(77L)).thenReturn(Optional.of(active));

        HostelAllocationResponse first = service.vacate(1L, 77L);
        HostelAllocationResponse again = service.vacate(1L, 77L);

        assertThat(first.status()).isEqualTo("VACATED");
        assertThat(first.vacatedByLoginId()).isEqualTo("admin1");
        assertThat(first.vacatedAt()).isNotNull();
        assertThat(again.vacatedAt()).isEqualTo(first.vacatedAt());
        verify(notifications, times(1)).vacated(active);
    }

    @Test
    void anApprovedApplicationCanBeAllocatedAgainAfterVacating() {
        // Nothing ACTIVE remains for the application, the student, or the bed after a vacate.
        assertThat(service.allocate(1L, 42L, 3L).status()).isEqualTo("ACTIVE");
        verify(allocations).existsActiveForApplication(42L);
        verify(allocations).existsActiveForStudent(5L);
    }
}
