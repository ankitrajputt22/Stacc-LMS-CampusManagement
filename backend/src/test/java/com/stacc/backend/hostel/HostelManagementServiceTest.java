package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.HostelRequests.CreateBedRequest;
import com.stacc.backend.hostel.HostelRequests.CreateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.CreateRoomRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateBedRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.UpdateRoomRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class HostelManagementServiceTest {

    private final HostelRepository hostels = mock(HostelRepository.class);
    private final HostelRoomRepository rooms = mock(HostelRoomRepository.class);
    private final HostelBedRepository beds = mock(HostelBedRepository.class);
    private final HostelAllocationRepository allocations = mock(HostelAllocationRepository.class);
    private final HostelManagementService service = new HostelManagementService(hostels, rooms, beds, allocations);

    private final Hostel main = HostelFixtures.hostel(1, "MAIN");
    private final HostelRoom room = HostelFixtures.room(2, main, "A-101");
    private final HostelBed bed = HostelFixtures.bed(3, room, "B1");

    @BeforeEach
    void setUp() {
        when(hostels.findById(1L)).thenReturn(Optional.of(main));
        when(hostels.findByIdForUpdate(1L)).thenReturn(Optional.of(main));
        when(rooms.findWithHostelById(2L)).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(2L)).thenReturn(Optional.of(room));
        when(beds.findByIdForUpdate(3L)).thenReturn(Optional.of(bed));
        when(rooms.findAllByHostelIdOrderByRoomNumberAscIdAsc(1L)).thenReturn(List.of(room));
        when(beds.findAllByRoomIdOrderByBedNumberAscIdAsc(2L)).thenReturn(List.of(bed));
        when(hostels.saveAndFlush(any())).thenAnswer(call -> HostelFixtures.withId(call.getArgument(0), 11));
        when(rooms.saveAndFlush(any())).thenAnswer(call -> {
            HostelRoom saved = call.getArgument(0);
            return saved.getId() == null ? HostelFixtures.withId(saved, 12) : saved;
        });
        when(beds.saveAndFlush(any())).thenAnswer(call -> {
            HostelBed saved = call.getArgument(0);
            if (saved.getId() == null) {
                HostelFixtures.withId(saved, 13);
                when(beds.findAllByRoomIdOrderByBedNumberAscIdAsc(2L)).thenReturn(List.of(bed, saved));
            }
            return saved;
        });
    }

    private static void assertRefused(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, refusal -> assertThat(refusal.getStatus()).isEqualTo(status));
    }

    @Test
    void hostelsAreCreatedActiveWithAnUppercaseCodeThatIsUnique() {
        assertThat(service.createHostel(new CreateHostelRequest("annex", "Annex", null)).code()).isEqualTo("ANNEX");

        when(hostels.existsByCode("MAIN")).thenReturn(true);
        assertRefused(() -> service.createHostel(new CreateHostelRequest("main", "Main", null)), HttpStatus.CONFLICT);
        assertRefused(() -> service.createHostel(new CreateHostelRequest("two words", "X", null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRaceForTheSameCodeIsStillAConflict() {
        doThrow(new DataIntegrityViolationException("uk_hostels_code")).when(hostels).saveAndFlush(any());
        assertThatThrownBy(() -> service.createHostel(new CreateHostelRequest("NEW", "New", null)))
                .hasMessage(HostelManagementService.CODE_TAKEN);
    }

    @Test
    void roomNumbersAreUniqueWithinAHostelOnly() {
        when(rooms.existsByHostelIdAndRoomNumber(1L, "A-101")).thenReturn(true);
        assertRefused(() -> service.createRoom(1L, new CreateRoomRequest(" A-101 ")), HttpStatus.CONFLICT);

        Hostel annex = HostelFixtures.hostel(5, "ANNEX");
        when(hostels.findById(5L)).thenReturn(Optional.of(annex));
        assertThat(service.createRoom(5L, new CreateRoomRequest("A-101")).roomNumber()).isEqualTo("A-101");
        assertRefused(() -> service.createRoom(404L, new CreateRoomRequest("A-1")), HttpStatus.NOT_FOUND);
    }

    @Test
    void bedNumbersAreUniqueWithinARoomOnly() {
        when(beds.existsByRoomIdAndBedNumber(2L, "B1")).thenReturn(true);
        assertRefused(() -> service.createBed(2L, new CreateBedRequest("B1")), HttpStatus.CONFLICT);

        assertThat(service.createBed(2L, new CreateBedRequest("B2")).occupancy()).isEqualTo("AVAILABLE");
    }

    @Test
    void anythingHoldingAnActiveAllocationCannotBeMadeInactive() {
        when(allocations.existsActiveForBed(3L)).thenReturn(true);
        when(allocations.existsActiveInRoom(2L)).thenReturn(true);
        when(allocations.existsActiveInHostel(1L)).thenReturn(true);

        assertRefused(() -> service.updateBed(3L, new UpdateBedRequest("B1", false)), HttpStatus.CONFLICT);
        assertRefused(() -> service.updateRoom(2L, new UpdateRoomRequest("A-101", false)), HttpStatus.CONFLICT);
        assertRefused(() -> service.updateHostel(1L, new UpdateHostelRequest("Main", null, false)), HttpStatus.CONFLICT);
        assertThat(bed.isActive()).isTrue();
        assertThat(room.isActive()).isTrue();
        assertThat(main.isActive()).isTrue();
    }

    @Test
    void afterVacatingTheyCanBeMadeInactive() {
        assertThat(service.updateBed(3L, new UpdateBedRequest("B1", false)).occupancy()).isEqualTo("INACTIVE");
        assertThat(service.updateRoom(2L, new UpdateRoomRequest("A-101", false)).active()).isFalse();
        assertThat(service.updateHostel(1L, new UpdateHostelRequest("Main", null, false)).active()).isFalse();
    }

    @Test
    void numbersStopChangingOnceThereIsAllocationHistory() {
        when(allocations.existsAnyInRoom(2L)).thenReturn(true);
        when(allocations.existsAnyForBed(3L)).thenReturn(true);

        assertRefused(() -> service.updateRoom(2L, new UpdateRoomRequest("A-201", true)), HttpStatus.CONFLICT);
        assertRefused(() -> service.updateBed(3L, new UpdateBedRequest("B9", true)), HttpStatus.CONFLICT);
        // The same number with another flag is fine.
        assertThat(service.updateRoom(2L, new UpdateRoomRequest("A-101", true)).roomNumber()).isEqualTo("A-101");
        assertThat(room.getRoomNumber()).isEqualTo("A-101");
    }

    @Test
    void numbersWithoutHistoryCanBeCorrected() {
        assertThat(service.updateRoom(2L, new UpdateRoomRequest("A-111", true)).roomNumber()).isEqualTo("A-111");
        assertThat(service.updateBed(3L, new UpdateBedRequest("B3", true)).bedNumber()).isEqualTo("B3");
    }

    @Test
    void theCodeIsNotPartOfAnUpdate() {
        service.updateHostel(1L, new UpdateHostelRequest("Main block", "Renovated.", true));
        assertThat(main.getCode()).isEqualTo("MAIN");
        assertThat(main.getName()).isEqualTo("Main block");
        verify(hostels, never()).existsByCode(any());
    }

    @Test
    void occupancyIsDerivedFromActiveAllocations() {
        when(allocations.findOccupiedBedIdsInRoom(2L)).thenReturn(List.of(3L));
        assertThat(service.beds(2L)).singleElement().satisfies(row -> assertThat(row.occupancy()).isEqualTo("OCCUPIED"));
    }
}
