package com.stacc.backend.devdata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.career.CareerCompanyRepository;
import com.stacc.backend.career.CareerOpportunity;
import com.stacc.backend.career.CareerOpportunityRepository;
import com.stacc.backend.career.CareerService;
import com.stacc.backend.engagement.events.CampusEventRepository;
import com.stacc.backend.engagement.events.CampusEventService;
import com.stacc.backend.engagement.facilities.CampusFacility;
import com.stacc.backend.engagement.facilities.CampusFacilityRepository;
import com.stacc.backend.engagement.facilities.CampusFacilityService;
import com.stacc.backend.engagement.groups.CampusGroup;
import com.stacc.backend.engagement.groups.CampusGroupOfficer;
import com.stacc.backend.engagement.groups.CampusGroupRepository;
import com.stacc.backend.engagement.groups.CampusGroupService;
import com.stacc.backend.grievance.GrievanceAdminService;
import com.stacc.backend.grievance.GrievanceCategoryRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelWardenRepository;
import com.stacc.backend.hostel.HostelWardenService;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.person.PersonProfileService;
import com.stacc.backend.mentoring.MentoringService;
import com.stacc.backend.support.helpdesk.HelpdeskTeamRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local demo data for the v0.8 to v0.12 areas, after {@link LocalTestDataSeeder}: staff accounts (warden1, security1,
 * maintenance1, librarian1, placement1) with the same test password, the Main Hostel's warden, the maintenance team,
 * a club and a sports group with officers, two facilities, a college event, and a placement opportunity. Like the main
 * seeder it only finds or creates, through the feature services, and never changes or deletes what exists.
 */
@Component
@Profile(LocalTestDataSeeder.PROFILE)
@Order(10)
public class LocalDemoExtrasSeeder implements ApplicationRunner {

    static final List<String> STAFF_LOGIN_IDS = List.of("warden1", "security1", "maintenance1", "librarian1", "placement1");
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDemoExtrasSeeder.class);

    private final Environment environment;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountRepository accounts;
    private final RoleRepository roles;
    private final PersonProfileRepository people;
    private final PersonProfileService personProfiles;
    private final HostelRepository hostels;
    private final HostelWardenRepository wardens;
    private final HostelWardenService wardenService;
    private final HelpdeskTeamRepository teams;
    private final HelpdeskTeamService teamService;
    private final CampusGroupRepository groups;
    private final CampusGroupService groupService;
    private final CampusFacilityRepository facilities;
    private final CampusFacilityService facilityService;
    private final CampusEventRepository events;
    private final CampusEventService eventService;
    private final CareerCompanyRepository companies;
    private final CareerOpportunityRepository opportunities;
    private final CareerService career;
    private final GrievanceCategoryRepository grievanceCategories;
    private final GrievanceAdminService grievanceAdmin;
    private final MentoringService mentoring;
    private final Clock clock;

    public LocalDemoExtrasSeeder(Environment environment, TransactionTemplate transaction, PasswordEncoder passwordEncoder,
            UserAccountRepository accounts, RoleRepository roles, PersonProfileRepository people, PersonProfileService personProfiles,
            HostelRepository hostels, HostelWardenRepository wardens, HostelWardenService wardenService, HelpdeskTeamRepository teams,
            HelpdeskTeamService teamService, CampusGroupRepository groups, CampusGroupService groupService, CampusFacilityRepository facilities,
            CampusFacilityService facilityService, CampusEventRepository events, CampusEventService eventService,
            CareerCompanyRepository companies, CareerOpportunityRepository opportunities, CareerService career,
            GrievanceCategoryRepository grievanceCategories, GrievanceAdminService grievanceAdmin, MentoringService mentoring,
            Clock clock) {
        this.environment = environment;
        this.transaction = transaction;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.roles = roles;
        this.people = people;
        this.personProfiles = personProfiles;
        this.hostels = hostels;
        this.wardens = wardens;
        this.wardenService = wardenService;
        this.teams = teams;
        this.teamService = teamService;
        this.groups = groups;
        this.groupService = groupService;
        this.facilities = facilities;
        this.facilityService = facilityService;
        this.events = events;
        this.eventService = eventService;
        this.companies = companies;
        this.opportunities = opportunities;
        this.career = career;
        this.grievanceCategories = grievanceCategories;
        this.grievanceAdmin = grievanceAdmin;
        this.mentoring = mentoring;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String password = LocalTestDataSeeder.checkedPassword(environment);
        transaction.executeWithoutResult(status -> staff(password));
        transaction.executeWithoutResult(status -> hostelAndSupport());
        transaction.executeWithoutResult(status -> campus());
        transaction.executeWithoutResult(status -> placement());
        transaction.executeWithoutResult(status -> grievances());
        LOGGER.info("Local demo extras ({} profile) ready. Staff accounts: {}.", LocalTestDataSeeder.PROFILE, String.join(", ", STAFF_LOGIN_IDS));
    }

    private void staff(String password) {
        account("warden1", password, "Kavita Rao", RoleName.WARDEN);
        account("security1", password, "Imran Khan", RoleName.SECURITY);
        account("maintenance1", password, "Suresh Kumar", RoleName.MAINTENANCE);
        account("librarian1", password, "Lata Menon", RoleName.LIBRARIAN);
        account("placement1", password, "Neha Verma", RoleName.PLACEMENT_OFFICER);
    }

    private void hostelAndSupport() {
        hostels.findByCode("MAIN").ifPresent(hostel -> {
            if (wardens.findActiveByHostel(hostel.getId()).isEmpty()) {
                wardenService.assign(hostel.getId(), "warden1", null);
            }
        });
        teams.findByCode("MAINTENANCE").ifPresent(team -> {
            if (teamService.all().stream().filter(view -> view.teamId().equals(team.getId())).allMatch(view -> view.memberLoginIds().isEmpty())) {
                teamService.setMembers(team.getId(), List.of("maintenance1", "warden1"));
            }
        });
    }

    private void campus() {
        if (!groups.existsByCode("CODING_CLUB")) {
            Long id = groupService.create("CODING_CLUB", "Coding Club", CampusGroup.Kind.CLUB,
                    "Weekly problem solving and project nights. Development demo data.", CampusGroup.JoinPolicy.APPROVAL).groupId();
            groupService.addOfficer(id, "faculty1", CampusGroupOfficer.Title.ADVISOR);
            groupService.addOfficer(id, "student1", CampusGroupOfficer.Title.PRESIDENT);
        }
        if (!groups.existsByCode("CRICKET")) {
            Long id = groupService.create("CRICKET", "Cricket Team", CampusGroup.Kind.SPORT,
                    "Practice on weekday evenings. Development demo data.", CampusGroup.JoinPolicy.OPEN).groupId();
            groupService.addOfficer(id, "faculty2", CampusGroupOfficer.Title.COACH);
        }
        if (!facilities.existsByCode("SEMINAR_HALL")) {
            facilityService.create("SEMINAR_HALL", "Seminar Hall", CampusFacility.Kind.HALL, "Main block, ground floor", 120,
                    LocalTime.of(8, 0), LocalTime.of(20, 0), true);
        }
        if (!facilities.existsByCode("BADMINTON_COURT")) {
            facilityService.create("BADMINTON_COURT", "Badminton Court", CampusFacility.Kind.COURT, "Sports complex", 4,
                    LocalTime.of(6, 0), LocalTime.of(21, 0), false);
        }
        String title = "Open Source Day (development)";
        if (events.findAllForAdmin().stream().noneMatch(event -> title.equals(event.getTitle()))) {
            Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();
            CampusEventService.Viewer viewer = new CampusEventService.Viewer(admin, false, false, true);
            Instant start = clock.instant().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.HOURS);
            Long id = eventService.create(viewer, new CampusEventService.EventInput(title,
                    "Talks and a first-contribution workshop. Development demo data.", null, "Seminar Hall", start,
                    start.plus(Duration.ofHours(3)), 30, null, null, true, true, false)).eventId();
            eventService.publish(viewer, id);
        }
    }

    private void grievances() {
        if (!grievanceCategories.existsByCode("ACADEMIC")) {
            Long admin = accounts.findByLoginId("admin1").orElseThrow().getId();
            Long categoryId = grievanceAdmin.create("ACADEMIC", "Academic matters", 14).categoryId();
            grievanceAdmin.addMember(admin, categoryId, "faculty1");
        }
        if (mentoring.allPairings().stream().noneMatch(pairing -> pairing.mentorLoginId().equals("faculty1")
                && pairing.menteeLoginId().equals("student1"))) {
            mentoring.pair("faculty1", "student1");
        }
    }

    private void placement() {
        Long companyId = companies.findAllByOrderByNameAsc().stream().filter(company -> company.getName().equals("Acme Software"))
                .findFirst().map(company -> company.getId())
                .orElseGet(() -> career.addCompany("Acme Software", "https://acme.example", "Development demo company.").getId());
        String title = "Graduate Software Engineer (development)";
        if (opportunities.findAllForManagers().stream().noneMatch(opportunity -> title.equals(opportunity.getTitle()))) {
            Long creator = accounts.findByLoginId("placement1").orElseThrow().getId();
            Long id = career.createOpportunity(creator, new CareerService.OpportunityInput(CareerOpportunity.Kind.PLACEMENT, title,
                    companyId, null, "A two-year graduate programme. Development demo data.", List.of(),
                    clock.instant().plus(Duration.ofDays(21)), List.of("Applied", "Online test", "Interview"))).opportunityId();
            career.setStatus(id, true);
        }
    }

    private void account(String loginId, String password, String name, RoleName role) {
        Optional<UserAccount> found = accounts.findByLoginId(loginId);
        UserAccount account = found.orElseGet(() -> new UserAccount(loginId, passwordEncoder.encode(password)));
        if (!account.hasRole(role)) {
            account.assignRole(roles.findByName(role).orElseThrow(() -> new IllegalStateException("Role " + role + " is missing")));
        }
        UserAccount saved = accounts.save(account);
        if (people.findByUserAccountId(saved.getId()).isEmpty()) {
            personProfiles.record(saved, name, loginId + "@stacc.example");
        }
    }
}
