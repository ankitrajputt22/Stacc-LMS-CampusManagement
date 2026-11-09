package com.stacc.backend.career;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Career profiles and the opportunities engine. Students keep their profile and resume versions, see open
 * opportunities with the reasons they are not eligible, apply with a resume version and their consent to share, follow
 * their stage, withdraw, and answer offers. Managers (CAREER_MANAGE) keep companies, create, open, and close
 * opportunities, schedule rounds, move applicants through stages, make offers, set the placement policy, and export
 * applicants who consented. Eligibility is decided here every time.
 */
@Service
@Transactional
public class CareerService {

    public record ProfileView(String headline, String skills, String links, List<ResumeView> resumes) {
    }

    public record ResumeView(Long resumeId, String label, String fileName, Instant createdAt, boolean archived) {
    }

    public record RoundView(int stageIndex, String stage, Instant startsAt, String venue) {
    }

    public record OpportunityView(Long opportunityId, CareerOpportunity.Kind kind, String title, String company, String organization,
            String description, Instant deadline, CareerOpportunity.Status status, List<String> stages, List<RoundView> rounds,
            List<Long> eligibleProgramIds, List<String> notEligibleBecause, String myApplicationStatus, long applicants) {
    }

    public record ApplicationView(Long applicationId, Long opportunityId, String opportunityTitle, String company, String applicantLoginId,
            String resumeLabel, boolean shareConsent, String stage, CareerApplication.Status status, Instant appliedAt, OfferView offer) {
    }

    public record OfferView(Long offerId, String roleTitle, String compensation, Instant respondBy, CareerOffer.Status status) {
    }

    public record OpportunityInput(CareerOpportunity.Kind kind, String title, Long companyId, String organization, String description,
            List<Long> programIds, Instant deadline, List<String> stages) {
    }

    private final CareerProfileRepository profiles;
    private final CareerResumeRepository resumes;
    private final CareerCompanyRepository companies;
    private final CareerOpportunityRepository opportunities;
    private final CareerRoundRepository rounds;
    private final CareerApplicationRepository applications;
    private final CareerOfferRepository offers;
    private final CareerPolicyRepository policy;
    private final StudentProfileRepository students;
    private final PersonProfileRepository people;
    private final UserAccountRepository accounts;
    private final LmsAttachmentStore files;
    private final Clock clock;

    public CareerService(CareerProfileRepository profiles, CareerResumeRepository resumes, CareerCompanyRepository companies,
            CareerOpportunityRepository opportunities, CareerRoundRepository rounds, CareerApplicationRepository applications,
            CareerOfferRepository offers, CareerPolicyRepository policy, StudentProfileRepository students, PersonProfileRepository people,
            UserAccountRepository accounts, LmsAttachmentStore files, Clock clock) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.companies = companies;
        this.opportunities = opportunities;
        this.rounds = rounds;
        this.applications = applications;
        this.offers = offers;
        this.policy = policy;
        this.students = students;
        this.people = people;
        this.accounts = accounts;
        this.files = files;
        this.clock = clock;
    }

    // --- Students: profile and resumes

    @Transactional(readOnly = true)
    public ProfileView profile(Long accountId) {
        Optional<CareerProfile> found = profiles.findByAccountId(accountId);
        return new ProfileView(found.map(CareerProfile::getHeadline).orElse(null), found.map(CareerProfile::getSkills).orElse(null),
                found.map(CareerProfile::getLinks).orElse(null),
                resumes.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream().map(CareerService::view).toList());
    }

    public ProfileView editProfile(Long accountId, String headline, String skills, String links) {
        CareerProfile profile = profiles.findByAccountId(accountId).orElseGet(() -> new CareerProfile(accountId, now()));
        try {
            profile.edit(headline, skills, links, now());
            profiles.saveAndFlush(profile);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The profile is not valid: " + invalid.getMessage() + ".");
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "Your profile changed at the same time. Try again.");
        }
        return profile(accountId);
    }

    public ProfileView addResume(Long accountId, String label, MultipartFile file) {
        List<StoredFile> stored = files.storeAll("career/accounts/" + accountId, file == null ? List.of() : List.of(file));
        if (stored.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a resume file.");
        }
        try {
            resumes.saveAndFlush(new CareerResume(accountId, label, stored.get(0), now()));
        } catch (RuntimeException failure) {
            files.removeQuietly(stored);
            throw failure instanceof IllegalArgumentException ? new ApiException(HttpStatus.BAD_REQUEST, "Give the resume a label.") : failure;
        }
        return profile(accountId);
    }

    public ProfileView archiveResume(Long accountId, Long resumeId) {
        ownResume(accountId, resumeId).archive(now());
        return profile(accountId);
    }

    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download openOwnResume(Long accountId, Long resumeId) {
        CareerResume resume = ownResume(accountId, resumeId);
        return files.open(resume.getObjectKey(), resume.getFileName(), resume.getContentType(), resume.getSizeBytes());
    }

    // --- Students: opportunities

    @Transactional(readOnly = true)
    public List<OpportunityView> open(Long accountId) {
        Map<Long, CareerApplication> mine = applications.findAllForApplicant(accountId).stream()
                .collect(Collectors.toMap(a -> a.getOpportunity().getId(), Function.identity(), (a, b) -> a));
        return opportunities.findAllByStatus(CareerOpportunity.Status.OPEN).stream()
                .map(o -> view(o, reasons(o, accountId, mine.get(o.getId())), mine.get(o.getId()), 0)).toList();
    }

    public ApplicationView apply(Long accountId, Long opportunityId, Long resumeId, String coverNote, boolean shareConsent) {
        CareerOpportunity opportunity = opportunities.findByIdForUpdate(opportunityId == null ? -1L : opportunityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Opportunity not found."));
        List<String> reasons = reasons(opportunity, accountId, null);
        if (applications.existsByOpportunityIdAndApplicantId(opportunity.getId(), accountId)) {
            reasons = List.of("You have already applied.");
        }
        if (!reasons.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, reasons.get(0));
        }
        CareerResume resume = resumeId == null ? null : ownResume(accountId, resumeId);
        if (resume != null && resume.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a current resume.");
        }
        try {
            return view(applications.saveAndFlush(new CareerApplication(opportunity, accounts.getReferenceById(accountId), resume, coverNote,
                    shareConsent, now())), null);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already applied.");
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> myApplications(Long accountId) {
        List<CareerApplication> mine = applications.findAllForApplicant(accountId);
        Map<Long, CareerOffer> byApplication = mine.isEmpty() ? Map.of() : offers.findAllForApplications(mine.stream().map(CareerApplication::getId).toList())
                .stream().collect(Collectors.toMap(f -> f.getApplication().getId(), Function.identity()));
        return mine.stream().map(a -> view(a, byApplication.get(a.getId()))).toList();
    }

    public ApplicationView withdraw(Long accountId, Long applicationId) {
        CareerApplication application = ownApplication(accountId, applicationId);
        try {
            application.withdraw();
        } catch (IllegalStateException finished) {
            throw new ApiException(HttpStatus.CONFLICT, "This application is already finished.");
        }
        return view(application, offers.findByApplicationId(application.getId()).orElse(null));
    }

    public ApplicationView answerOffer(Long accountId, Long applicationId, boolean accept) {
        CareerApplication application = ownApplication(accountId, applicationId);
        CareerOffer offer = offers.findByApplicationId(application.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found."));
        if (accept && application.getOpportunity().getKind() == CareerOpportunity.Kind.PLACEMENT && policy().isOneAcceptedPlacement()
                && offers.hasAcceptedPlacement(accountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "The college's policy allows one accepted placement offer.");
        }
        try {
            offer.respond(accept, now());
        } catch (IllegalStateException answered) {
            throw new ApiException(HttpStatus.CONFLICT, "This offer was already answered.");
        }
        return view(application, offer);
    }

    // --- Managers

    @Transactional(readOnly = true)
    public List<CareerCompany> companies() {
        return companies.findAllByOrderByNameAsc();
    }

    public CareerCompany addCompany(String name, String website, String description) {
        if (name != null && companies.existsByName(name.strip())) {
            throw new ApiException(HttpStatus.CONFLICT, "A company with this name already exists.");
        }
        try {
            return companies.saveAndFlush(new CareerCompany(name, website, description, now()));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The company is not valid: " + invalid.getMessage() + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<OpportunityView> managed() {
        return opportunities.findAllForManagers().stream()
                .map(o -> view(o, List.of(), null, applications.findAllForOpportunity(o.getId()).size())).toList();
    }

    public OpportunityView createOpportunity(Long managerId, OpportunityInput input) {
        CareerOpportunity opportunity = new CareerOpportunity(managerId);
        edit(opportunity, input);
        return view(opportunities.saveAndFlush(opportunity), List.of(), null, 0);
    }

    public OpportunityView editOpportunity(Long opportunityId, OpportunityInput input) {
        CareerOpportunity opportunity = lockedOpportunity(opportunityId);
        edit(opportunity, input);
        return view(opportunity, List.of(), null, 0);
    }

    public OpportunityView setStatus(Long opportunityId, boolean open) {
        CareerOpportunity opportunity = lockedOpportunity(opportunityId);
        if (open) {
            opportunity.open();
        } else {
            opportunity.close();
        }
        return view(opportunity, List.of(), null, 0);
    }

    public OpportunityView scheduleRound(Long opportunityId, int stageIndex, Instant startsAt, String venue) {
        CareerOpportunity opportunity = lockedOpportunity(opportunityId);
        if (stageIndex < 0 || stageIndex >= opportunity.stageNames().size() || startsAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose one of the stages and a time.");
        }
        rounds.findOne(opportunityId, stageIndex).ifPresentOrElse(round -> round.schedule(startsAt, venue),
                () -> rounds.saveAndFlush(new CareerRound(opportunity, stageIndex, startsAt, venue)));
        return view(opportunity, List.of(), null, 0);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> applicants(Long opportunityId) {
        lockedOpportunityRead(opportunityId);
        List<CareerApplication> found = applications.findAllForOpportunity(opportunityId);
        Map<Long, CareerOffer> byApplication = found.isEmpty() ? Map.of() : offers.findAllForApplications(found.stream().map(CareerApplication::getId).toList())
                .stream().collect(Collectors.toMap(f -> f.getApplication().getId(), Function.identity()));
        return found.stream().map(a -> view(a, byApplication.get(a.getId()))).toList();
    }

    public ApplicationView move(Long applicationId, boolean advance) {
        CareerApplication application = applications.findByIdForUpdate(applicationId == null ? -1L : applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
        try {
            if (advance) {
                application.advance(application.getOpportunity().stageNames().size());
            } else {
                application.reject();
            }
        } catch (IllegalStateException finished) {
            throw new ApiException(HttpStatus.CONFLICT, "This application is already finished.");
        }
        return view(application, offers.findByApplicationId(application.getId()).orElse(null));
    }

    public ApplicationView makeOffer(Long applicationId, String roleTitle, String compensation, Instant respondBy) {
        CareerApplication application = applications.findByIdForUpdate(applicationId == null ? -1L : applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
        if (application.getStatus() != CareerApplication.Status.SELECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Make an offer only to a selected applicant.");
        }
        if (offers.findByApplicationId(application.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This applicant already has an offer.");
        }
        try {
            return view(application, offers.saveAndFlush(new CareerOffer(application, roleTitle, compensation, respondBy, now())));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The offer needs a role.");
        }
    }

    @Transactional(readOnly = true)
    public boolean oneAcceptedPlacement() {
        return policy().isOneAcceptedPlacement();
    }

    public boolean setPolicy(boolean oneAcceptedPlacement) {
        CareerPolicy current = policy();
        current.change(oneAcceptedPlacement, now());
        return current.isOneAcceptedPlacement();
    }

    /** Rows for the applicant export: only applicants who consented to share, with what they shared. */
    @Transactional(readOnly = true)
    public List<List<String>> exportRows(Long opportunityId) {
        CareerOpportunity opportunity = lockedOpportunityRead(opportunityId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Login ID", "Name", "Program", "Status", "Stage", "Resume", "Applied"));
        for (CareerApplication a : applications.findAllForOpportunity(opportunityId)) {
            if (!a.isShareConsent()) {
                continue;
            }
            Long id = a.getApplicant().getId();
            rows.add(List.of(a.getApplicant().getLoginId(),
                    people.findByUserAccountId(id).map(PersonProfile::displayName).orElse(""),
                    students.findByUserAccountId(id).map(p -> p.getProgram().getCode()).orElse(""),
                    a.getStatus().name(), opportunity.stageNames().get(Math.min(a.getStageIndex(), opportunity.stageNames().size() - 1)),
                    a.getResume() == null ? "" : a.getResume().getLabel(), a.getAppliedAt().toString()));
        }
        return rows;
    }

    // --- Shared

    private List<String> reasons(CareerOpportunity opportunity, Long accountId, CareerApplication existing) {
        List<String> reasons = new ArrayList<>();
        if (existing != null) {
            return reasons;
        }
        if (opportunity.getStatus() != CareerOpportunity.Status.OPEN) {
            reasons.add("This opportunity is not open.");
        }
        if (!now().isBefore(opportunity.getDeadline())) {
            reasons.add("The deadline has passed.");
        }
        Optional<StudentProfile> student = students.findByUserAccountId(accountId).filter(p -> p.getStatus() == StudentProfileStatus.ACTIVE);
        if (student.isEmpty()) {
            reasons.add("Only active students can apply.");
        } else if (!opportunity.programIds().isEmpty() && !opportunity.programIds().contains(student.get().getProgram().getId())) {
            reasons.add("Your program is not eligible.");
        }
        if (opportunity.getKind() == CareerOpportunity.Kind.PLACEMENT && policy().isOneAcceptedPlacement() && offers.hasAcceptedPlacement(accountId)) {
            reasons.add("You have already accepted a placement offer.");
        }
        return reasons;
    }

    private void edit(CareerOpportunity opportunity, OpportunityInput input) {
        CareerCompany company = input.companyId() == null ? null : companies.findById(input.companyId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a known company."));
        try {
            opportunity.edit(input.kind(), input.title(), company, input.organization(), input.description(), input.programIds(),
                    input.deadline(), input.stages());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The opportunity is not valid: " + invalid.getMessage() + ".");
        } catch (IllegalStateException notDraft) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a draft can be edited.");
        }
    }

    private CareerPolicy policy() {
        return policy.findById(1L).orElseThrow();
    }

    private CareerResume ownResume(Long accountId, Long resumeId) {
        return resumes.findById(resumeId == null ? -1L : resumeId).filter(r -> r.getAccountId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found."));
    }

    private CareerApplication ownApplication(Long accountId, Long applicationId) {
        return applications.findByIdForUpdate(applicationId == null ? -1L : applicationId)
                .filter(a -> a.getApplicant().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
    }

    private CareerOpportunity lockedOpportunity(Long id) {
        return opportunities.findByIdForUpdate(id == null ? -1L : id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Opportunity not found."));
    }

    private CareerOpportunity lockedOpportunityRead(Long id) {
        return opportunities.findById(id == null ? -1L : id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Opportunity not found."));
    }

    private OpportunityView view(CareerOpportunity o, List<String> reasons, CareerApplication mine, long applicants) {
        List<String> stages = o.stageNames();
        List<RoundView> roundViews = o.getId() == null ? List.of() : rounds.findAllForOpportunities(List.of(o.getId())).stream()
                .map(r -> new RoundView(r.getStageIndex(), stages.get(Math.min(r.getStageIndex(), stages.size() - 1)), r.getStartsAt(), r.getVenue()))
                .toList();
        return new OpportunityView(o.getId(), o.getKind(), o.getTitle(), o.getCompany() == null ? null : o.getCompany().getName(),
                o.getOrganization(), o.getDescription(), o.getDeadline(), o.getStatus(), stages, roundViews, o.programIds(), reasons,
                mine == null ? null : mine.getStatus().name(), applicants);
    }

    private ApplicationView view(CareerApplication a, CareerOffer offer) {
        List<String> stages = a.getOpportunity().stageNames();
        return new ApplicationView(a.getId(), a.getOpportunity().getId(), a.getOpportunity().getTitle(),
                a.getOpportunity().getCompany() == null ? a.getOpportunity().getOrganization() : a.getOpportunity().getCompany().getName(),
                a.getApplicant().getLoginId(), a.getResume() == null ? null : a.getResume().getLabel(), a.isShareConsent(),
                stages.get(Math.min(a.getStageIndex(), stages.size() - 1)), a.getStatus(), a.getAppliedAt(),
                offer == null ? null : new OfferView(offer.getId(), offer.getRoleTitle(), offer.getCompensation(), offer.getRespondBy(), offer.getStatus()));
    }

    private static ResumeView view(CareerResume r) {
        return new ResumeView(r.getId(), r.getLabel(), r.getFileName(), r.getCreatedAt(), r.getArchivedAt() != null);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
