package com.stacc.backend.hostel.notice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.hostel.Hostel;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelWardenService;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hostel notices. The hostel office manages notices of every hostel; a warden only those of their own hostels (decided
 * by {@link HostelWardenService#isWardenOf}); any other hostel answers 404. Residents read the published notices of
 * their current hostel that reach their block. Publishing an emergency notice for the first time tells every resident
 * it reaches, once, under the notice's row lock.
 */
@Service
@Transactional
public class HostelNoticeService {

    static final String NOT_FOUND = "Notice not found.";
    static final String HOSTEL_NOT_FOUND = "Hostel not found.";

    /** Who is managing: the account, and whether it acts as the hostel office (admin) or as a warden. */
    public record Manager(Long accountId, boolean hostelOffice) {
    }

    public record NoticeView(Long noticeId, Long hostelId, String hostelName, String block, String title, String message,
            boolean emergency, HostelNotice.Status status, Instant publishedAt, Instant updatedAt) {

        static NoticeView of(HostelNotice notice) {
            return new NoticeView(notice.getId(), notice.getHostel().getId(), notice.getHostel().getName(), notice.getBlock(),
                    notice.getTitle(), notice.getMessage(), notice.isEmergency(), notice.getStatus(), notice.getPublishedAt(),
                    notice.getUpdatedAt());
        }
    }

    private final HostelNoticeRepository notices;
    private final HostelRepository hostels;
    private final HostelAllocationRepository allocations;
    private final HostelWardenService wardens;
    private final NotificationService notifications;
    private final Clock clock;

    public HostelNoticeService(HostelNoticeRepository notices, HostelRepository hostels, HostelAllocationRepository allocations,
            HostelWardenService wardens, NotificationService notifications, Clock clock) {
        this.notices = notices;
        this.hostels = hostels;
        this.allocations = allocations;
        this.wardens = wardens;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NoticeView> forHostel(Manager manager, Long hostelId) {
        requireHostel(manager, hostelId);
        return notices.findAllForHostel(hostelId).stream().map(NoticeView::of).toList();
    }

    public NoticeView add(Manager manager, Long hostelId, String block, String title, String message, boolean emergency) {
        requireHostel(manager, hostelId);
        Hostel hostel = hostels.findById(hostelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND));
        return NoticeView.of(notices.saveAndFlush(valid(() -> new HostelNotice(hostel, block, title, message, emergency, manager.accountId()))));
    }

    public NoticeView edit(Manager manager, Long noticeId, String title, String message, boolean emergency) {
        HostelNotice notice = locked(manager, noticeId);
        valid(() -> {
            notice.edit(title, message, emergency);
            return notice;
        });
        return NoticeView.of(notices.saveAndFlush(notice));
    }

    /** Publishes a notice; the first publication of an emergency notice tells every resident it reaches. */
    public NoticeView publish(Manager manager, Long noticeId) {
        HostelNotice notice = locked(manager, noticeId);
        boolean first = valid(() -> notice.publish(clock.instant()));
        notices.saveAndFlush(notice);
        if (first && notice.isEmergency()) {
            List<Long> residents = allocations.findActiveInHostel(notice.getHostel().getId()).stream()
                    .filter(allocation -> notice.reaches(allocation.getBed().getRoom().getBlock()))
                    .map(HostelNoticeService::accountOf)
                    .distinct()
                    .toList();
            notifications.notifyUsers(new NotificationMessage(NotificationType.HOSTEL_EMERGENCY, "Hostel emergency: " + notice.getTitle(),
                    notice.getMessage().length() > 500 ? notice.getMessage().substring(0, 499) + "…" : notice.getMessage(),
                    NotificationSourceType.HOSTEL_NOTICE, notice.getId(), "/hostel"), residents);
        }
        return NoticeView.of(notice);
    }

    public NoticeView archive(Manager manager, Long noticeId) {
        HostelNotice notice = locked(manager, noticeId);
        notice.archive();
        return NoticeView.of(notices.saveAndFlush(notice));
    }

    /** The published notices of the resident's current hostel that reach their block; none without a bed. */
    @Transactional(readOnly = true)
    public List<NoticeView> forResident(Long accountId) {
        Optional<HostelAllocation> allocation = allocations.findActiveForStudentAccount(accountId);
        if (allocation.isEmpty()) {
            return List.of();
        }
        String block = allocation.get().getBed().getRoom().getBlock();
        return notices.findPublishedForHostel(allocation.get().getBed().getRoom().getHostel().getId()).stream()
                .filter(notice -> notice.reaches(block)).map(NoticeView::of).toList();
    }

    private void requireHostel(Manager manager, Long hostelId) {
        if (hostelId == null || !(manager.hostelOffice() || wardens.isWardenOf(manager.accountId(), hostelId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND);
        }
    }

    private HostelNotice locked(Manager manager, Long noticeId) {
        Long hostelId = noticeId == null ? null : notices.findHostelIdById(noticeId).orElse(null);
        if (hostelId == null || !(manager.hostelOffice() || wardens.isWardenOf(manager.accountId(), hostelId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return notices.findByIdForUpdate(noticeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private static Long accountOf(HostelAllocation allocation) {
        return allocation.getHostelApplication().getStudentProfile().getUserAccount().getId();
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
