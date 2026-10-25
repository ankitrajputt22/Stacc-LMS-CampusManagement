package com.stacc.backend.hostel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import org.junit.jupiter.api.Test;

class HostelEntitiesTest {

    private final UserAccount studentAccount = HostelFixtures.account(70, "student1", RoleName.STUDENT);
    private final UserAccount admin = HostelFixtures.account(1, "admin1", RoleName.ADMIN);

    @Test
    void hostelCodesAreUppercaseAndChecked() {
        assertThat(new Hostel(" main ", "Main Hostel", null).getCode()).isEqualTo("MAIN");
        assertThat(new Hostel("block-b_2", "Block B", " ").getDescription()).isNull();
        assertThatThrownBy(() -> new Hostel("main hall", "Main", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Hostel("X".repeat(51), "Main", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBedIsAllocatableOnlyWhenItItsRoomAndItsHostelAreActive() {
        Hostel hostel = HostelFixtures.hostel(1, "MAIN");
        HostelRoom room = HostelFixtures.room(2, hostel, "A-101");
        HostelBed bed = HostelFixtures.bed(3, room, "B1");
        assertThat(bed.isAllocatable()).isTrue();

        bed.update("B1", false);
        assertThat(bed.isAllocatable()).isFalse();
        bed.update("B1", true);
        room.update("A-101", false);
        assertThat(bed.isAllocatable()).isFalse();
        room.update("A-101", true);
        hostel.update(hostel.getName(), null, false);
        assertThat(bed.isAllocatable()).isFalse();
    }

    @Test
    void anApplicationIsNumberedOnceAndReadsItsStatusFromTheApprovalRequest() {
        HostelApplication application = HostelFixtures.application(42, HostelFixtures.student(5, studentAccount),
                HostelFixtures.session(2, "2026-27", 2026), null);
        assertThat(application.getApplicationNumber()).isEqualTo("HST-000042");
        assertThat(application.approvalStatus()).isNull();

        var approval = HostelFixtures.approval(12, studentAccount);
        application.linkApproval(approval);
        assertThat(application.approvalStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
        approval.approve(approval.currentStep().orElseThrow(), admin, null, HostelFixtures.NOW);
        assertThat(application.approvalStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThatThrownBy(() -> application.linkApproval(approval)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neitherTheApplicationNorTheBedStoresAStatusOrOccupancyOfItsOwn() {
        assertThat(Arrays.stream(HostelApplication.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("status", "approvalStatus");
        assertThat(Arrays.stream(HostelBed.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("occupied", "status");
        assertThat(Arrays.stream(HostelRoom.class.getDeclaredFields()).map(Field::getName)).doesNotContain("capacity");
    }

    @Test
    void vacatingRecordsWhoAndWhenOnce() {
        HostelApplication application = HostelFixtures.application(42, HostelFixtures.student(5, studentAccount),
                HostelFixtures.session(2, "2026-27", 2026), HostelFixtures.approved(12, studentAccount, admin));
        HostelAllocation allocation = new HostelAllocation(application,
                HostelFixtures.bed(3, HostelFixtures.room(2, HostelFixtures.hostel(1, "MAIN"), "A-101"), "B1"), admin,
                HostelFixtures.NOW);
        assertThat(allocation.getStatus()).isEqualTo(HostelAllocationStatus.ACTIVE);

        allocation.vacate(admin, HostelFixtures.NOW.plusSeconds(60));

        assertThat(allocation.getStatus()).isEqualTo(HostelAllocationStatus.VACATED);
        assertThat(allocation.getVacatedBy()).isSameAs(admin);
        assertThat(allocation.getVacatedAt()).isEqualTo(Instant.parse("2026-10-26T09:31:00.123456Z"));
        assertThatThrownBy(() -> allocation.vacate(admin, HostelFixtures.NOW)).isInstanceOf(IllegalStateException.class);
    }
}
