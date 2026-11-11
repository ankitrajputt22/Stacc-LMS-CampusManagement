package com.stacc.backend.projects;

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
 * Projects (v0.12.10), one team model. A student creates a project and leads it; other students ask to join an open
 * project and the lead accepts them only while active members are fewer than the limit (the project row lock, then a
 * locking read of the active members). The lead invites active faculty as mentors, who accept or decline. Students and
 * faculty see projects that are not archived, with members and mentors by login ID only.
 */
@Service
@Transactional
public class ProjectService {

    static final String NOT_FOUND = "Project not found.";
    static final String LEAD_ONLY = "Only the project's lead can do this.";
    static final String FULL = "The project has no free places.";
    static final String NOT_OPEN = "This project is not taking join requests.";
    static final String ALREADY = "You have already asked to join or are on this project.";
    static final String NOT_FACULTY = "Invite an active faculty account.";
    static final String ALREADY_MENTOR = "That faculty member is already invited or mentoring.";

    public record MemberView(Long memberId, String loginId, ProjectMember.Status status, boolean lead, String message) {
    }

    public record MentorView(Long mentorId, String loginId, ProjectMentor.Status status) {
    }

    /** A project; join requests and their messages are sent to the lead only. */
    public record ProjectView(Long projectId, String title, String summary, String leadLoginId, int maxMembers, long activeMembers,
            Project.Status status, boolean iAmLead, String myMembership, Long myMemberId, String myMentorship, Long myMentorId,
            List<MemberView> members, List<MentorView> mentors) {
    }

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectMentorRepository mentors;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectMentorRepository mentors,
            UserAccountRepository accounts, Clock clock) {
        this.projects = projects;
        this.members = members;
        this.mentors = mentors;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(Long accountId) {
        return views(projects.findListed(), accountId);
    }

    public ProjectView create(Long accountId, String title, String summary, int maxMembers) {
        UserAccount lead = accounts.getReferenceById(accountId);
        Project saved = projects.saveAndFlush(valid(() -> new Project(lead, title, summary, maxMembers, now())));
        members.saveAndFlush(new ProjectMember(saved, lead, null, true, now()));
        return one(saved, accountId);
    }

    public ProjectView edit(Long accountId, Long projectId, String title, String summary, int maxMembers) {
        Project project = led(accountId, projectId);
        if (maxMembers < members.findActiveForUpdate(project.getId()).size()) {
            throw new ApiException(HttpStatus.CONFLICT, "The limit cannot be below the current members.");
        }
        valid(() -> {
            project.edit(title, summary, maxMembers);
            return project;
        });
        return one(project, accountId);
    }

    public ProjectView setStatus(Long accountId, Long projectId, Project.Status status) {
        Project project = led(accountId, projectId);
        valid(() -> {
            project.setStatus(status);
            return project;
        });
        return one(project, accountId);
    }

    public ProjectView requestToJoin(Long accountId, Long projectId, String message) {
        Project project = locked(projectId);
        if (project.getStatus() != Project.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_OPEN);
        }
        try {
            members.saveAndFlush(valid(() -> new ProjectMember(project, accounts.getReferenceById(accountId), message, false, now())));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY);
        }
        return one(project, accountId);
    }

    /** The lead accepts within the member limit, or declines. */
    public ProjectView decide(Long accountId, Long memberId, boolean accept) {
        Project project = led(accountId, projectOfMember(memberId));
        ProjectMember request = members.findByIdForUpdate(memberId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (accept && members.findActiveForUpdate(project.getId()).size() >= project.getMaxMembers()) {
            throw new ApiException(HttpStatus.CONFLICT, FULL);
        }
        valid(() -> {
            request.decide(accept, now());
            return request;
        });
        members.flush();
        return one(project, accountId);
    }

    /** Withdraws one's own request or leaves; the lead stays with the project. */
    public ProjectView leave(Long accountId, Long memberId) {
        Project project = locked(projectOfMember(memberId));
        ProjectMember place = members.findByIdForUpdate(memberId).filter(found -> found.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        if (project.getLead().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "The lead cannot leave the project.");
        }
        valid(() -> {
            place.withdrawOrLeave(now());
            return place;
        });
        members.flush();
        return one(project, accountId);
    }

    public ProjectView inviteMentor(Long accountId, Long projectId, String loginId) {
        Project project = led(accountId, projectId);
        UserAccount faculty = (loginId == null ? Optional.<UserAccount>empty() : accounts.findWithRolesAndPermissionsByLoginId(loginId.strip()))
                .filter(found -> found.getStatus() == AccountStatus.ACTIVE && found.hasRole(RoleName.FACULTY))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_FACULTY));
        try {
            mentors.saveAndFlush(new ProjectMentor(project, faculty, now()));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_MENTOR);
        }
        return one(project, accountId);
    }

    /** The invited faculty member accepts or declines; an accepted mentor may later step down. */
    public ProjectView answerMentorship(Long accountId, Long mentorId, Boolean accept) {
        ProjectMentor mentor = (mentorId == null ? Optional.<ProjectMentor>empty() : mentors.findById(mentorId))
                .filter(found -> found.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        Project project = locked(mentor.getProject().getId());
        valid(() -> {
            if (accept == null) {
                mentor.end(now());
            } else {
                mentor.answer(accept, now());
            }
            return mentor;
        });
        mentors.flush();
        return one(project, accountId);
    }

    private Long projectOfMember(Long memberId) {
        return (memberId == null ? Optional.<Long>empty() : members.findProjectIdOf(memberId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private Project locked(Long projectId) {
        return (projectId == null ? Optional.<Project>empty() : projects.findByIdForUpdate(projectId))
                .filter(found -> found.getStatus() != Project.Status.ARCHIVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private Project led(Long accountId, Long projectId) {
        Project project = locked(projectId);
        if (!project.getLead().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, LEAD_ONLY);
        }
        return project;
    }

    private ProjectView one(Project project, Long accountId) {
        return views(List.of(project), accountId).getFirst();
    }

    private List<ProjectView> views(List<Project> found, Long accountId) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(Project::getId).toList();
        Map<Long, List<ProjectMember>> places = members.findLiveFor(ids).stream().collect(Collectors.groupingBy(member -> member.getProject().getId()));
        Map<Long, List<ProjectMentor>> mentorships = mentors.findLiveFor(ids).stream().collect(Collectors.groupingBy(mentor -> mentor.getProject().getId()));
        return found.stream().map(project -> {
            boolean lead = project.getLead().getId().equals(accountId);
            List<ProjectMember> live = places.getOrDefault(project.getId(), List.of());
            List<ProjectMentor> mentoring = mentorships.getOrDefault(project.getId(), List.of());
            Optional<ProjectMember> mine = live.stream().filter(member -> member.getAccount().getId().equals(accountId)).findFirst();
            Optional<ProjectMentor> mineAsMentor = mentoring.stream().filter(mentor -> mentor.getAccount().getId().equals(accountId)).findFirst();
            List<MemberView> shown = live.stream()
                    .filter(member -> lead || member.getStatus() == ProjectMember.Status.ACTIVE || member.getAccount().getId().equals(accountId))
                    .map(member -> new MemberView(member.getId(), member.getAccount().getLoginId(), member.getStatus(),
                            member.getAccount().getId().equals(project.getLead().getId()), lead ? member.getMessage() : null))
                    .toList();
            return new ProjectView(project.getId(), project.getTitle(), project.getSummary(), project.getLead().getLoginId(), project.getMaxMembers(),
                    live.stream().filter(member -> member.getStatus() == ProjectMember.Status.ACTIVE).count(), project.getStatus(), lead,
                    mine.map(member -> member.getStatus().name()).orElse(null), mine.map(ProjectMember::getId).orElse(null),
                    mineAsMentor.map(mentor -> mentor.getStatus().name()).orElse(null), mineAsMentor.map(ProjectMentor::getId).orElse(null), shown,
                    mentoring.stream().map(mentor -> new MentorView(mentor.getId(), mentor.getAccount().getLoginId(), mentor.getStatus())).toList());
        }).toList();
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
