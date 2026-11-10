package com.stacc.backend.mentoring;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mentoring (v0.12.4). A pairing, its meetings, and its action items open only for its mentor and its student; anyone
 * else gets the same 404 as for a missing one, and there is no admin pass to the meetings. Only the mentor records and
 * edits meetings, sets action items, and reads private notes. Admins pair and end pairings.
 */
@Service
@Transactional
public class MentoringService {

    static final String NOT_FOUND = "Mentoring record not found.";
    static final String MENTOR_ONLY = "Only the mentor can do this.";
    static final String ENDED = "This mentoring pair has ended.";
    static final String NOT_ELIGIBLE = "A mentor is an active faculty account and a mentee an active student account.";
    static final String ALREADY = "These two are already paired.";

    public record PairingView(Long assignmentId, String mentorLoginId, String menteeLoginId, boolean iAmMentor, boolean active,
            Instant startedAt, Instant endedAt) {
    }

    public record ActionItemView(Long itemId, String description, MentoringActionItem.Owner owner, Instant dueAt, Instant completedAt) {
    }

    /** A meeting; private notes are sent to the mentor only and are null for the student. */
    public record MeetingView(Long meetingId, String title, Instant heldAt, String sharedNotes, String privateNotes, List<ActionItemView> actionItems) {
    }

    public record PairingDetail(PairingView pairing, List<MeetingView> meetings) {
    }

    public record OpenItem(Long itemId, String description, Instant dueAt, String otherLoginId, boolean asMentor) {
    }

    private final MentorAssignmentRepository assignments;
    private final MentoringMeetingRepository meetings;
    private final MentoringActionItemRepository items;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public MentoringService(MentorAssignmentRepository assignments, MentoringMeetingRepository meetings, MentoringActionItemRepository items,
            UserAccountRepository accounts, Clock clock) {
        this.assignments = assignments;
        this.meetings = meetings;
        this.items = items;
        this.accounts = accounts;
        this.clock = clock;
    }

    // --- The pair

    @Transactional(readOnly = true)
    public List<PairingView> pairings(Long accountId) {
        return assignments.findForAccount(accountId).stream().map(found -> view(found, accountId)).toList();
    }

    @Transactional(readOnly = true)
    public PairingDetail pairing(Long accountId, Long assignmentId) {
        MentorAssignment found = member(accountId, assignmentId);
        return detail(found, accountId);
    }

    public PairingDetail addMeeting(Long accountId, Long assignmentId, Instant heldAt, String title, String sharedNotes, String privateNotes) {
        MentorAssignment found = mentorOf(accountId, assignmentId);
        meetings.saveAndFlush(valid(() -> new MentoringMeeting(found, heldAt, title, sharedNotes, privateNotes, now())));
        return detail(found, accountId);
    }

    public PairingDetail editMeeting(Long accountId, Long meetingId, Instant heldAt, String title, String sharedNotes, String privateNotes) {
        MentoringMeeting meeting = meeting(meetingId);
        MentorAssignment found = mentorOf(accountId, meeting.getAssignment().getId());
        valid(() -> {
            meeting.edit(heldAt, title, sharedNotes, privateNotes);
            return meeting;
        });
        meetings.flush();
        return detail(found, accountId);
    }

    public PairingDetail addActionItem(Long accountId, Long meetingId, String description, MentoringActionItem.Owner owner, Instant dueAt) {
        MentoringMeeting meeting = meeting(meetingId);
        MentorAssignment found = mentorOf(accountId, meeting.getAssignment().getId());
        items.saveAndFlush(valid(() -> new MentoringActionItem(meeting, description, owner, dueAt, now())));
        return detail(found, accountId);
    }

    /** Either of the pair marks an item done; again changes nothing. */
    public PairingDetail completeActionItem(Long accountId, Long itemId) {
        MentoringActionItem item = (itemId == null ? Optional.<MentoringActionItem>empty() : items.findById(itemId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        MentorAssignment found = member(accountId, item.getMeeting().getAssignment().getId());
        item.complete(now());
        items.flush();
        return detail(found, accountId);
    }

    /** Open action items the account owns in its active pairings, for the To-Do list. */
    @Transactional(readOnly = true)
    public List<OpenItem> openItems(Long accountId) {
        return items.findOpenOwnedBy(accountId).stream().map(item -> {
            MentorAssignment pair = item.getMeeting().getAssignment();
            boolean asMentor = item.getOwner() == MentoringActionItem.Owner.MENTOR;
            return new OpenItem(item.getId(), item.getDescription(), item.getDueAt(),
                    asMentor ? pair.getMentee().getLoginId() : pair.getMentor().getLoginId(), asMentor);
        }).toList();
    }

    // --- Admins

    @Transactional(readOnly = true)
    public List<PairingView> allPairings() {
        return assignments.findAllForAdmin().stream().map(found -> view(found, null)).toList();
    }

    public PairingView pair(String mentorLoginId, String menteeLoginId) {
        UserAccount mentor = eligible(mentorLoginId, RoleName.FACULTY);
        UserAccount mentee = eligible(menteeLoginId, RoleName.STUDENT);
        try {
            return view(assignments.saveAndFlush(valid(() -> new MentorAssignment(mentor, mentee, now()))), null);
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY);
        }
    }

    public PairingView end(Long assignmentId) {
        MentorAssignment found = (assignmentId == null ? Optional.<MentorAssignment>empty() : assignments.findById(assignmentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        found.end(now());
        assignments.flush();
        return view(found, null);
    }

    private UserAccount eligible(String loginId, RoleName role) {
        return (loginId == null ? Optional.<UserAccount>empty() : accounts.findWithRolesAndPermissionsByLoginId(loginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE && found.hasRole(role))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_ELIGIBLE));
    }

    private MentorAssignment member(Long accountId, Long assignmentId) {
        return (assignmentId == null ? Optional.<MentorAssignment>empty() : assignments.findById(assignmentId))
                .filter(found -> found.getMentor().getId().equals(accountId) || found.getMentee().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private MentorAssignment mentorOf(Long accountId, Long assignmentId) {
        MentorAssignment found = member(accountId, assignmentId);
        if (!found.getMentor().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, MENTOR_ONLY);
        }
        if (!found.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ENDED);
        }
        return found;
    }

    private MentoringMeeting meeting(Long meetingId) {
        return (meetingId == null ? Optional.<MentoringMeeting>empty() : meetings.findById(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private PairingDetail detail(MentorAssignment found, Long accountId) {
        boolean mentor = found.getMentor().getId().equals(accountId);
        List<MentoringMeeting> held = meetings.findForAssignment(found.getId());
        Map<Long, List<MentoringActionItem>> byMeeting = held.isEmpty() ? Map.of()
                : items.findForMeetings(held.stream().map(MentoringMeeting::getId).toList()).stream()
                        .collect(Collectors.groupingBy(item -> item.getMeeting().getId()));
        return new PairingDetail(view(found, accountId), held.stream().map(meeting -> new MeetingView(meeting.getId(), meeting.getTitle(),
                meeting.getHeldAt(), meeting.getSharedNotes(), mentor ? meeting.getPrivateNotes() : null,
                byMeeting.getOrDefault(meeting.getId(), List.of()).stream().map(item -> new ActionItemView(item.getId(), item.getDescription(),
                        item.getOwner(), item.getDueAt(), item.getCompletedAt())).toList())).toList());
    }

    private static PairingView view(MentorAssignment found, Long accountId) {
        return new PairingView(found.getId(), found.getMentor().getLoginId(), found.getMentee().getLoginId(),
                found.getMentor().getId().equals(accountId), found.isActive(), found.getStartedAt(), found.getEndedAt());
    }

    private static <T> T valid(Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
