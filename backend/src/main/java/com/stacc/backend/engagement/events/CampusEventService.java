package com.stacc.backend.engagement.events;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.approval.ApprovalCommands.ApprovalRequestOutcome;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalRequestStatus;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.groups.CampusGroup;
import com.stacc.backend.engagement.groups.CampusGroupMembership;
import com.stacc.backend.engagement.groups.CampusGroupMembershipRepository;
import com.stacc.backend.engagement.groups.CampusGroupRepository;
import com.stacc.backend.engagement.groups.CampusGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campus events. Admins (CAMPUS_ENGAGEMENT_MANAGE) create and publish any event; a group's officers create events for
 * their group, which their faculty advisor approves (GROUP_EVENT workflow) before they show. An event's shown status is
 * worked out from its approval on read. Registration checks eligibility on the server and gives reasons; it locks the
 * event row, then reads the places with a locking read, so a capacity is never passed: a full event puts people on the
 * waitlist, and a cancelled place goes to the first person waiting.
 */
@Service
@Transactional
public class CampusEventService {

    public static final String WORKFLOW_CODE = "GROUP_EVENT";
    public static final String SOURCE_TYPE = "CAMPUS_EVENT";

    /** Who is asking: the account and what it holds, from the token. */
    public record Viewer(Long accountId, boolean student, boolean faculty, boolean admin) {
    }

    public record EventView(Long eventId, String title, String description, Long groupId, String groupName, String organizerLoginId,
            String venue, Instant startsAt, Instant endsAt, Integer capacity, Instant registrationOpensAt, Instant registrationClosesAt,
            boolean forStudents, boolean forFaculty, boolean membersOnly, String status, long registered, long waitlisted,
            String myRegistration, Integer myWaitlistPosition, List<String> cannotRegisterBecause, boolean canManage, boolean trial) {
    }

    public record EventInput(String title, String description, Long groupId, String venue, Instant startsAt, Instant endsAt,
            Integer capacity, Instant registrationOpensAt, Instant registrationClosesAt, boolean forStudents, boolean forFaculty,
            boolean membersOnly) {
    }

    public record AttendeeView(String loginId, String status, Instant registeredAt) {
    }

    private final CampusEventRepository events;
    private final CampusEventRegistrationRepository registrations;
    private final CampusGroupRepository groups;
    private final CampusGroupMembershipRepository memberships;
    private final CampusGroupService groupService;
    private final UserAccountRepository accounts;
    private final ApprovalWorkflowService approvals;
    private final ApprovalQueryService approvalQueries;
    private final Clock clock;

    public CampusEventService(CampusEventRepository events, CampusEventRegistrationRepository registrations, CampusGroupRepository groups,
            CampusGroupMembershipRepository memberships, CampusGroupService groupService, UserAccountRepository accounts,
            ApprovalWorkflowService approvals, ApprovalQueryService approvalQueries, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.groups = groups;
        this.memberships = memberships;
        this.groupService = groupService;
        this.accounts = accounts;
        this.approvals = approvals;
        this.approvalQueries = approvalQueries;
        this.clock = clock;
    }

    // --- Reading

    /** Events people can see: published (or approved) and cancelled ones that have not ended, soonest first. */
    @Transactional(readOnly = true)
    public List<EventView> upcoming(Viewer viewer) {
        return events.findShowable(now()).stream().map(event -> view(event, viewer))
                .filter(view -> view.status().equals("PUBLISHED") || view.status().equals("CANCELLED")).toList();
    }

    @Transactional(readOnly = true)
    public EventView one(Viewer viewer, Long eventId) {
        CampusEvent event = events.findById(eventId == null ? -1L : eventId).orElseThrow(CampusEventService::missing);
        EventView view = view(event, viewer);
        if (!view.canManage() && !(view.status().equals("PUBLISHED") || view.status().equals("CANCELLED"))) {
            throw missing();
        }
        return view;
    }

    /** The events the viewer may manage: all for admins, otherwise their own and their groups'. */
    @Transactional(readOnly = true)
    public List<EventView> managed(Viewer viewer) {
        List<CampusEvent> found = viewer.admin() ? events.findAllForAdmin() : events.findManagedBy(viewer.accountId());
        return found.stream().map(event -> view(event, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendeeView> attendees(Viewer viewer, Long eventId) {
        CampusEvent event = events.findById(eventId == null ? -1L : eventId).orElseThrow(CampusEventService::missing);
        if (!canManage(event, viewer)) {
            throw missing();
        }
        return registrations.findCurrent(eventId).stream()
                .map(r -> new AttendeeView(r.getAccount().getLoginId(), r.getStatus().name(), r.getRegisteredAt())).toList();
    }

    // --- Organizing

    public EventView create(Viewer viewer, EventInput input) {
        CampusGroup group = null;
        if (input.groupId() != null) {
            group = groups.findById(input.groupId()).filter(CampusGroup::isActive).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose an active group."));
            if (!viewer.admin() && !groupService.isOfficer(viewer.accountId(), group.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Only the group's officers can organize its events.");
            }
        } else if (!viewer.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Choose one of your groups; college-wide events are made by the college.");
        }
        CampusEvent event = new CampusEvent(accounts.getReferenceById(viewer.accountId()), group);
        apply(event, input);
        return view(events.saveAndFlush(event), viewer);
    }

    public EventView edit(Viewer viewer, Long eventId, EventInput input) {
        CampusEvent event = managedLocked(viewer, eventId);
        apply(event, input);
        return view(event, viewer);
    }

    /** Publishes a draft: at once for admins and for events without a group; a group event goes to its advisor first. */
    public EventView publish(Viewer viewer, Long eventId) {
        CampusEvent event = managedLocked(viewer, eventId);
        String status = statusOf(event);
        if (!status.equals("DRAFT")) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a draft can be published.");
        }
        if (event.getGroup() == null || viewer.admin()) {
            event.publish(now());
        } else {
            event.awaitApproval(approvals.startApproval(new StartApprovalCommand(WORKFLOW_CODE, viewer.accountId(), SOURCE_TYPE,
                    String.valueOf(event.getId()), "Event: " + event.getTitle())).approvalRequestId());
        }
        return view(event, viewer);
    }

    public EventView cancel(Viewer viewer, Long eventId) {
        CampusEvent event = managedLocked(viewer, eventId);
        if (event.getApprovalRequestId() != null) {
            approvals.cancelApprovalIfPending(event.getApprovalRequestId(), viewer.accountId());
        }
        event.cancel(now());
        return view(event, viewer);
    }

    // --- Registering

    public EventView register(Viewer viewer, Long eventId) {
        CampusEvent event = events.findByIdForUpdate(eventId == null ? -1L : eventId).orElseThrow(CampusEventService::missing);
        List<CampusEventRegistration> current = registrations.findCurrentForUpdate(event.getId());
        List<String> reasons = reasons(event, viewer, statusOf(event), current);
        if (!reasons.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, reasons.get(0));
        }
        long registered = current.stream().filter(r -> r.getStatus() == CampusEventRegistration.Status.REGISTERED).count();
        boolean full = event.getCapacity() != null && registered >= event.getCapacity();
        registrations.saveAndFlush(new CampusEventRegistration(event, accounts.getReferenceById(viewer.accountId()), full, now()));
        return view(event, viewer);
    }

    /** Gives up a place or leaves the waitlist; a freed place goes to the first person waiting. */
    public EventView unregister(Viewer viewer, Long eventId) {
        CampusEvent event = events.findByIdForUpdate(eventId == null ? -1L : eventId).orElseThrow(CampusEventService::missing);
        List<CampusEventRegistration> current = registrations.findCurrentForUpdate(event.getId());
        CampusEventRegistration mine = current.stream().filter(r -> r.getAccount().getId().equals(viewer.accountId())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "You are not registered for this event."));
        boolean hadPlace = mine.getStatus() == CampusEventRegistration.Status.REGISTERED;
        Instant now = now();
        mine.cancel(now);
        if (hadPlace && now.isBefore(event.getStartsAt())) {
            current.stream().filter(r -> r.getStatus() == CampusEventRegistration.Status.WAITLISTED).findFirst()
                    .ifPresent(next -> next.promote(now));
        }
        return view(event, viewer);
    }

    // --- Shared

    /** The event's shown status: a pending group event follows its approval. */
    String statusOf(CampusEvent event) {
        if (event.getStatus() != CampusEvent.Status.PENDING_APPROVAL || event.getApprovalRequestId() == null) {
            return event.getStatus().name();
        }
        ApprovalRequestStatus approval = approvalQueries.requestStatus(event.getApprovalRequestId())
                .map(ApprovalRequestOutcome::status).orElse(ApprovalRequestStatus.PENDING);
        return switch (approval) {
            case APPROVED -> "PUBLISHED";
            case REJECTED, CANCELLED -> "DRAFT";
            default -> "PENDING_APPROVAL";
        };
    }

    private List<String> reasons(CampusEvent event, Viewer viewer, String status, List<CampusEventRegistration> current) {
        List<String> reasons = new ArrayList<>();
        Instant now = now();
        if (!status.equals("PUBLISHED")) {
            reasons.add(status.equals("CANCELLED") ? "This event was cancelled." : "This event is not open yet.");
            return reasons;
        }
        if (!now.isBefore(event.getEndsAt())) {
            reasons.add("This event is over.");
        }
        if (!((event.isForStudents() && viewer.student()) || (event.isForFaculty() && viewer.faculty()))) {
            reasons.add(event.isForStudents() && !event.isForFaculty() ? "This event is for students." : "This event is for faculty.");
        }
        if (event.isMembersOnly() && event.getGroup() != null && memberships.findCurrentForAccount(viewer.accountId()).stream()
                .noneMatch(m -> m.getGroup().getId().equals(event.getGroup().getId()) && m.getStatus() == CampusGroupMembership.Status.ACTIVE)) {
            reasons.add("This event is for members of " + event.getGroup().getName() + ".");
        }
        if (event.getRegistrationOpensAt() != null && now.isBefore(event.getRegistrationOpensAt())) {
            reasons.add("Registration has not opened yet.");
        }
        Instant closes = event.getRegistrationClosesAt() != null ? event.getRegistrationClosesAt() : event.getStartsAt();
        if (!now.isBefore(closes)) {
            reasons.add("Registration has closed.");
        }
        if (current.stream().anyMatch(r -> r.getAccount().getId().equals(viewer.accountId()))) {
            reasons.add("You are already registered or on the waitlist.");
        }
        return reasons;
    }

    private EventView view(CampusEvent event, Viewer viewer) {
        String status = statusOf(event);
        List<CampusEventRegistration> current = registrations.findCurrent(event.getId());
        List<CampusEventRegistration> waiting = current.stream().filter(r -> r.getStatus() == CampusEventRegistration.Status.WAITLISTED).toList();
        Optional<CampusEventRegistration> mine = current.stream().filter(r -> r.getAccount().getId().equals(viewer.accountId())).findFirst();
        Integer position = mine.filter(r -> r.getStatus() == CampusEventRegistration.Status.WAITLISTED).map(r -> waiting.indexOf(r) + 1).orElse(null);
        return new EventView(event.getId(), event.getTitle(), event.getDescription(),
                event.getGroup() == null ? null : event.getGroup().getId(), event.getGroup() == null ? null : event.getGroup().getName(),
                event.getOrganizer().getLoginId(), event.getVenue(), event.getStartsAt(), event.getEndsAt(), event.getCapacity(),
                event.getRegistrationOpensAt(), event.getRegistrationClosesAt(), event.isForStudents(), event.isForFaculty(),
                event.isMembersOnly(), status, current.size() - waiting.size(), waiting.size(),
                mine.map(r -> r.getStatus().name()).orElse(null), position,
                mine.isPresent() ? List.of() : reasons(event, viewer, status, current), canManage(event, viewer), event.isTrial());
    }

    boolean canManage(CampusEvent event, Viewer viewer) {
        return viewer.admin() || event.getOrganizer().getId().equals(viewer.accountId())
                || (event.getGroup() != null && groupService.isOfficer(viewer.accountId(), event.getGroup().getId()));
    }

    private CampusEvent managedLocked(Viewer viewer, Long eventId) {
        CampusEvent event = events.findByIdForUpdate(eventId == null ? -1L : eventId).orElseThrow(CampusEventService::missing);
        if (!canManage(event, viewer)) {
            throw missing();
        }
        // A refused group event goes back to being a draft that can be corrected.
        if (event.getStatus() == CampusEvent.Status.PENDING_APPROVAL && statusOf(event).equals("DRAFT")) {
            event.backToDraft();
        }
        return event;
    }

    private void apply(CampusEvent event, EventInput input) {
        try {
            event.edit(input.title(), input.description(), input.venue(), input.startsAt(), input.endsAt(), input.capacity(),
                    input.registrationOpensAt(), input.registrationClosesAt(), input.forStudents(), input.forFaculty(), input.membersOnly());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The event is not valid: " + invalid.getMessage() + ".");
        } catch (IllegalStateException notDraft) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a draft event can be edited.");
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "Event not found.");
    }
}
