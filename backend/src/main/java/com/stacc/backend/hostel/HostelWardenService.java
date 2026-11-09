package com.stacc.backend.hostel;

import java.time.Clock;
import java.util.List;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wardens of hostels: admins with HOSTEL_MANAGE assign an active account holding WARDEN to a hostel (optionally one
 * block) and end assignments; nothing is deleted. Whether an account is a warden of a hostel is answered here only.
 */
@Service
@Transactional
public class HostelWardenService {

    public record WardenView(Long wardenAssignmentId, Long hostelId, String loginId, String block,
            java.time.Instant assignedAt, java.time.Instant endedAt) {

        static WardenView of(HostelWarden warden) {
            return new WardenView(warden.getId(), warden.getHostel().getId(), warden.getWarden().getLoginId(), warden.getBlock(),
                    warden.getAssignedAt(), warden.getEndedAt());
        }
    }

    private final HostelWardenRepository wardens;
    private final HostelRepository hostels;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public HostelWardenService(HostelWardenRepository wardens, HostelRepository hostels, UserAccountRepository accounts, Clock clock) {
        this.wardens = wardens;
        this.hostels = hostels;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WardenView> wardensOf(Long hostelId) {
        hostel(hostelId);
        return wardens.findAllByHostel(hostelId).stream().map(WardenView::of).toList();
    }

    public WardenView assign(Long hostelId, String loginId, String block) {
        Hostel hostel = hostel(hostelId);
        UserAccount account = accounts.findByLoginId(loginId == null ? "" : loginId.strip())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No account has this login ID."));
        if (account.getStatus() != AccountStatus.ACTIVE || !account.hasRole(RoleName.WARDEN)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only an active account holding the warden role can be a warden.");
        }
        boolean already = wardens.findActiveByHostel(hostelId).stream().anyMatch(w -> w.getWarden().getId().equals(account.getId())
                && java.util.Objects.equals(w.getBlock(), block == null || block.isBlank() ? null : block.strip()));
        if (already) {
            throw new ApiException(HttpStatus.CONFLICT, "This warden is already assigned here.");
        }
        try {
            return WardenView.of(wardens.saveAndFlush(new HostelWarden(hostel, account, block, clock.instant())));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage() + ".");
        }
    }

    /** Ends an assignment; ending it again changes nothing. */
    public WardenView end(Long assignmentId) {
        HostelWarden warden = wardens.findByIdForUpdate(assignmentId == null ? -1L : assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Warden assignment not found."));
        warden.end(clock.instant());
        return WardenView.of(warden);
    }

    @Transactional(readOnly = true)
    public boolean isWardenOf(Long accountId, Long hostelId) {
        return accountId != null && hostelId != null && wardens.findActiveHostelIds(accountId).contains(hostelId);
    }

    @Transactional(readOnly = true)
    public List<Long> hostelsOf(Long accountId) {
        return wardens.findActiveHostelIds(accountId);
    }

    private Hostel hostel(Long hostelId) {
        return hostels.findById(hostelId == null ? -1L : hostelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hostel not found."));
    }
}
