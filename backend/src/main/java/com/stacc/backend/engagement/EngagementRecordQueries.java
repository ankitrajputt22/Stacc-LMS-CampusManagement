package com.stacc.backend.engagement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.stacc.backend.engagement.events.CampusEventRegistrationRepository;
import com.stacc.backend.engagement.fests.FestVolunteerRepository;
import com.stacc.backend.engagement.teams.CampusTeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An account's own campus participation, read live from the engagement records for its portfolio: events attended
 * (checked in), accepted fest volunteering, and team places. Read only; nothing is copied.
 */
@Service
@Transactional(readOnly = true)
public class EngagementRecordQueries {

    public enum Kind { EVENT_ATTENDED, VOLUNTEERED, TEAM }

    public record Participation(Kind kind, String title, String detail, Instant at) {
    }

    private final CampusEventRegistrationRepository registrations;
    private final FestVolunteerRepository volunteers;
    private final CampusTeamMemberRepository players;

    public EngagementRecordQueries(CampusEventRegistrationRepository registrations, FestVolunteerRepository volunteers,
            CampusTeamMemberRepository players) {
        this.registrations = registrations;
        this.volunteers = volunteers;
        this.players = players;
    }

    public List<Participation> participation(Long accountId) {
        List<Participation> found = new ArrayList<>();
        registrations.findAttendedByAccount(accountId).forEach(registration -> found.add(new Participation(Kind.EVENT_ATTENDED,
                registration.getEvent().getTitle(), "Attended", registration.getAttendedAt())));
        volunteers.findAcceptedByAccount(accountId).forEach(volunteer -> found.add(new Participation(Kind.VOLUNTEERED,
                volunteer.getShift().getFest().getName(), "Volunteer: " + volunteer.getShift().getRoleTitle(), volunteer.getShift().getStartsAt())));
        players.findForAccount(accountId).forEach(player -> found.add(new Participation(Kind.TEAM,
                player.getTeam().getName() + " (" + player.getTeam().getSeason() + ")",
                player.getTeam().getGroup().getName() + " · " + switch (player.getRole()) {
                    case CAPTAIN -> "Captain";
                    case VICE_CAPTAIN -> "Vice-captain";
                    case PLAYER -> "Player";
                }, player.getJoinedAt())));
        found.sort(Comparator.comparing(Participation::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return found;
    }
}
