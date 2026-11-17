package com.stacc.backend.devdata;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.engagement.groups.CampusGroupMembership;
import com.stacc.backend.engagement.groups.CampusGroupService;
import com.stacc.backend.library.LibraryService;
import com.stacc.backend.library.LibraryTitleRepository;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.discussion.LmsDiscussionService;
import com.stacc.backend.mentoring.MentoringActionItem;
import com.stacc.backend.mentoring.MentoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Everyday campus activity for the people of {@link LocalCollegePopulation}, after the main seeder and the demo
 * extras: course discussions with answers, club and team memberships, mentoring pairs with a meeting, and library
 * loans. Like the other seeders it only finds or creates, through the feature services, and skips everything when the
 * population is missing.
 */
@Component
@Profile(LocalTestDataSeeder.PROFILE)
@Order(20)
public class LocalCampusActivitySeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCampusActivitySeeder.class);

    private final Environment environment;
    private final TransactionTemplate transaction;
    private final UserAccountRepository accounts;
    private final LmsStudentCourseQueryService studentCourses;
    private final LmsDiscussionService discussions;
    private final CampusGroupService groups;
    private final MentoringService mentoring;
    private final LibraryTitleRepository libraryTitles;
    private final LibraryService library;
    private final Clock clock;

    public LocalCampusActivitySeeder(Environment environment, TransactionTemplate transaction, UserAccountRepository accounts,
            LmsStudentCourseQueryService studentCourses, LmsDiscussionService discussions, CampusGroupService groups,
            MentoringService mentoring, LibraryTitleRepository libraryTitles, LibraryService library, Clock clock) {
        this.environment = environment;
        this.transaction = transaction;
        this.accounts = accounts;
        this.studentCourses = studentCourses;
        this.discussions = discussions;
        this.groups = groups;
        this.mentoring = mentoring;
        this.libraryTitles = libraryTitles;
        this.library = library;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        LocalTestDataSeeder.checkedPassword(environment);
        if (accounts.findByLoginId("ece25001").isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(status -> discussions());
        transaction.executeWithoutResult(status -> clubs());
        transaction.executeWithoutResult(status -> mentoring());
        transaction.executeWithoutResult(status -> library());
        LOGGER.info("Local campus activity ({} profile) ready.", LocalTestDataSeeder.PROFILE);
    }

    // --- Course discussions: questions with answers, one accepted, and a pinned notice from the teacher.

    private void discussions() {
        Optional<Long> signals = courseOf("ece25001", "EC301");
        signals.ifPresent(course -> {
            thread(course, "ece25003", "Is a sine wave an energy signal or a power signal?",
                    "A sine wave goes on forever, so its energy is infinite. Does that make it a power signal?",
                    List.of(new Reply("emp3002", AuthorKind.FACULTY, "Yes. Its energy is infinite but its average power is "
                            + "A squared over 2, which is finite, so it is a power signal.", true),
                            new Reply("ece25007", AuthorKind.STUDENT, "This helped me with problem 9 of the problem set too.", false)),
                    false);
            thread(course, "ece25010", "Period of a sum of two sinusoids",
                    "In tutorial problem 4, how do we find the period of cos(2t) + sin(3t)?",
                    List.of(new Reply("ece25002", AuthorKind.STUDENT, "Find each period (pi and 2 pi / 3), then the smallest "
                            + "time that is a whole number of both: 2 pi.", false)),
                    false);
            thread(course, "emp3002", "Extra office hours before the problem set deadline",
                    "I will be in ECE-204 on Thursday from 4 to 6 pm for questions on the problem sets.", List.of(), true);
        });
        courseOf("cse25006", "CS301").ifPresent(course -> thread(course, "cse25006",
                "Deleting the last node of a doubly linked list",
                "When I delete the tail, do I also need to update the head if the list had one node?",
                List.of(new Reply("emp1021", AuthorKind.FACULTY, "Yes. With one node the head and the tail are the same node, "
                        + "so both become null.", true)),
                false));
    }

    private record Reply(String loginId, AuthorKind kind, String body, boolean accepted) {
    }

    private void thread(Long lmsCourseId, String authorLoginId, String title, String body, List<Reply> replies, boolean pinned) {
        Long authorId = id(authorLoginId);
        AuthorKind authorKind = authorLoginId.startsWith("emp") || authorLoginId.startsWith("faculty")
                ? AuthorKind.FACULTY : AuthorKind.STUDENT;
        Viewer author = new Viewer(authorId, authorKind);
        boolean exists = discussions.list(author, lmsCourseId).stream()
                .anyMatch(summary -> summary.thread().getTitle().equals(title));
        if (exists) {
            return;
        }
        Long threadId = discussions.start(author, lmsCourseId, title, body).thread().getId();
        for (Reply reply : replies) {
            var detail = discussions.reply(new Viewer(id(reply.loginId()), reply.kind()), lmsCourseId, threadId, reply.body());
            if (reply.accepted()) {
                Long replyId = detail.replies().getLast().getId();
                discussions.accept(author, lmsCourseId, threadId, replyId);
            }
        }
        if (pinned) {
            discussions.pin(author, lmsCourseId, threadId, true);
        }
    }

    private Optional<Long> courseOf(String studentLoginId, String courseCode) {
        return studentCourses.getAccessibleCourses(id(studentLoginId)).stream()
                .filter(course -> course.courseCode().equals(courseCode))
                .map(LmsCourseSummaryResponse::lmsCourseId)
                .findFirst();
    }

    // --- Clubs and teams: requests waiting for the Coding Club's officers, two accepted, and open cricket sign-ups.

    private void clubs() {
        Long emp1021 = id("emp1021");
        groups.list(emp1021, false).forEach(group -> {
            if (group.code().equals("CODING_CLUB")) {
                for (String loginId : List.of("cse25004", "cse25005", "cse25009", "cse25013", "ece25006")) {
                    join(loginId, group.groupId());
                }
                // The advisor accepts the first two requests; the others wait.
                groups.members(emp1021, false, group.groupId()).stream()
                        .filter(member -> member.status() == CampusGroupMembership.Status.PENDING)
                        .filter(member -> member.loginId().equals("cse25004") || member.loginId().equals("cse25005"))
                        .forEach(member -> groups.decide(emp1021, false, member.membershipId(), true));
            }
            if (group.code().equals("CRICKET")) {
                for (String loginId : List.of("ece25001", "ece25009", "cse25010", "cse25014", "ece25013")) {
                    join(loginId, group.groupId());
                }
            }
        });
    }

    private void join(String loginId, Long groupId) {
        Long accountId = id(loginId);
        boolean already = groups.list(accountId, false).stream()
                .anyMatch(group -> group.groupId().equals(groupId) && group.myMembership() != null);
        if (!already) {
            groups.join(accountId, groupId);
        }
    }

    // --- Mentoring: the ECE head mentors two students who miss class, with a first meeting and an action item.

    private void mentoring() {
        pair("emp3001", "ece25005", true);
        pair("emp3001", "ece25012", false);
        pair("emp2011", "cse25008", false);
    }

    private void pair(String mentor, String mentee, boolean withMeeting) {
        boolean exists = mentoring.allPairings().stream()
                .anyMatch(pairing -> pairing.mentorLoginId().equals(mentor) && pairing.menteeLoginId().equals(mentee));
        if (exists) {
            return;
        }
        Long assignmentId = mentoring.pair(mentor, mentee).assignmentId();
        if (withMeeting) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
            Long mentorId = id(mentor);
            var detail = mentoring.addMeeting(mentorId, assignmentId, now.minus(3, ChronoUnit.DAYS), "Attendance check-in",
                    "We talked about the classes missed in Signals and Systems and a plan to catch up.",
                    "Commutes two hours each way; consider a hostel place.");
            Long meetingId = detail.meetings().getFirst().meetingId();
            mentoring.addActionItem(mentorId, meetingId, "Attend every Signals and Systems class for the next two weeks",
                    MentoringActionItem.Owner.MENTEE, now.plus(14, ChronoUnit.DAYS));
            mentoring.addActionItem(mentorId, meetingId, "Check the hostel waiting list", MentoringActionItem.Owner.MENTOR,
                    now.plus(7, ChronoUnit.DAYS));
        }
    }

    // --- Library: department textbooks, two of them on loan.

    private void library() {
        if (!libraryTitles.existsByIsbn("9780138147570")) {
            Long titleId = library.addTitle("Signals and Systems", "Alan V. Oppenheim, Alan S. Willsky", "9780138147570",
                    "Pearson", 1997, "Electronics").titleId();
            library.addCopy(titleId, "LIB-0101", "EC-A1");
            library.addCopy(titleId, "LIB-0102", "EC-A1");
            library.issue(id("stf2014"), "LIB-0101", "ece25003");
        }
        if (!libraryTitles.existsByIsbn("9780190853464")) {
            Long titleId = library.addTitle("Microelectronic Circuits", "Adel S. Sedra, Kenneth C. Smith", "9780190853464",
                    "Oxford University Press", 2019, "Electronics").titleId();
            library.addCopy(titleId, "LIB-0103", "EC-A2");
            library.issue(id("stf2014"), "LIB-0103", "ece25008");
        }
    }

    private Long id(String loginId) {
        return accounts.findByLoginId(loginId).map(UserAccount::getId)
                .orElseThrow(() -> new IllegalStateException("Account " + loginId + " is missing"));
    }
}
