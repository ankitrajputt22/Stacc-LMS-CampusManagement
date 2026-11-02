package com.stacc.backend.academic.registration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import com.stacc.backend.academic.admin.OfferingFacultyLoader;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.offering.OfferingCapacity;
import com.stacc.backend.academic.offering.OfferingRegistrationStatus;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.EligibleOffering;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegisteredCourse;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationOverview;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationResult;
import com.stacc.backend.academic.registration.StudentRegistrationRecords.RegistrationTerm;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Students register for course offerings of their own semester and section, and drop them before the drop deadline.
 * The student always comes from the access token. Registration is all or nothing: every chosen offering is checked
 * under row locks (the student's semester enrollments, then the offerings in ID order) before any enrollment is made,
 * so a capacity is never exceeded. Nothing is deleted: a dropped enrollment stays as history.
 */
@Service
@Transactional
public class StudentRegistrationService {

    static final String NO_PROFILE = "You do not have a student profile.";
    static final String PROFILE_INACTIVE = "Your student profile is inactive, so you cannot register or drop courses.";
    static final String REPEATED = "Choose each course offering only once.";
    static final String OFFERING_MISSING = "One of the chosen course offerings does not exist.";
    static final String ENROLLMENT_MISSING = "Course enrollment not found.";
    static final String NOT_ENROLLED = "Only a course you are enrolled in can be dropped.";
    static final String NO_DROPPING = "Courses of this semester cannot be dropped.";
    static final String DROP_PASSED = "The drop deadline for this semester has passed.";

    static final String REASON_REGISTERED = "You are registered for this course.";
    static final String REASON_ENDED = "You dropped or ended this course earlier, so it cannot be taken again.";
    static final String REASON_PROFILE = "Your student profile is inactive.";
    static final String REASON_CLOSED = "Registration is not open now.";
    static final String REASON_FULL = "This course is full.";
    static final String REASON_CREDITS = "This course would take you over your credit limit.";

    private final StudentProfileRepository profiles;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseOfferingRepository offerings;
    private final OfferingFacultyLoader faculty;
    private final Clock clock;

    public StudentRegistrationService(StudentProfileRepository profiles, SemesterEnrollmentRepository semesterEnrollments,
            CourseEnrollmentRepository courseEnrollments, CourseOfferingRepository offerings,
            OfferingFacultyLoader faculty, Clock clock) {
        this.profiles = profiles;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.offerings = offerings;
        this.faculty = faculty;
        this.clock = clock;
    }

    /** The student's registration page: each semester they may register in now, its offerings, and their courses. */
    @Transactional(readOnly = true)
    public RegistrationOverview overview(Long userAccountId) {
        StudentProfile profile = profileOf(userAccountId);
        boolean active = profile.getStatus() == StudentProfileStatus.ACTIVE;
        Instant now = clock.instant();
        List<SemesterEnrollment> terms = registrationTerms(profile);
        if (terms.isEmpty()) {
            return new RegistrationOverview(active, List.of());
        }

        Map<Long, List<CourseOffering>> openBySemester = new LinkedHashMap<>();
        for (SemesterEnrollment term : terms) {
            openBySemester.put(term.getSemester().getId(), offerings
                    .findAllWithContext(term.getSemester().getId(), null, null, null, Pageable.unpaged()).stream()
                    .filter(offering -> isOpenTo(offering, term))
                    .toList());
        }
        Map<Long, List<CourseEnrollment>> enrollmentsByTerm = courseEnrollments
                .findAllWithOfferingBySemesterEnrollmentIdIn(terms.stream().map(SemesterEnrollment::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(enrollment -> enrollment.getSemesterEnrollment().getId()));

        Set<Long> offeringIds = new HashSet<>();
        openBySemester.values().forEach(list -> list.forEach(offering -> offeringIds.add(offering.getId())));
        enrollmentsByTerm.values().forEach(list -> list.forEach(enrollment -> offeringIds.add(
                enrollment.getCourseOffering().getId())));
        Map<Long, Long> enrolledCounts = enrolledCounts(offeringIds);
        Map<Long, List<String>> facultyNames = facultyNames(offeringIds);

        List<RegistrationTerm> answer = new ArrayList<>();
        for (SemesterEnrollment term : terms) {
            Semester semester = term.getSemester();
            List<CourseEnrollment> mine = enrollmentsByTerm.getOrDefault(term.getId(), List.of());
            Map<Long, CourseEnrollment> mineByOffering = mine.stream()
                    .collect(Collectors.toMap(enrollment -> enrollment.getCourseOffering().getId(), e -> e));
            BigDecimal registeredCredits = enrolledCredits(mine);
            BigDecimal maxCredits = semester.getProgram().getMaxCreditsPerSemester();
            boolean open = semester.isRegistrationOpenAt(now);
            boolean dropOpen = active && semester.isDropAllowedAt(now);

            List<EligibleOffering> eligible = openBySemester.get(semester.getId()).stream()
                    .map(offering -> {
                        Integer free = freePlaces(offering, enrolledCounts);
                        String reason = reason(offering, mineByOffering.get(offering.getId()), active, open, free,
                                registeredCredits, maxCredits);
                        return new EligibleOffering(offering.getId(), offering.getCourse().getCode(),
                                offering.getCourse().getName(), offering.getCourse().getCredits(),
                                offering.getCourse().getCourseType().name(), sectionCode(offering),
                                facultyNames.getOrDefault(offering.getId(), List.of()), offering.getCapacity(), free,
                                reason == null, reason);
                    })
                    .toList();
            List<RegisteredCourse> registered = mine.stream()
                    .map(enrollment -> registeredCourse(enrollment, facultyNames, dropOpen))
                    .toList();
            answer.add(new RegistrationTerm(semester.getAcademicSession().getCode(), semester.getProgram().getCode(),
                    semester.getProgram().getName(), semester.getSemesterNumber(),
                    term.getSection() == null ? null : term.getSection().getCode(), semester.getRegistrationOpensAt(),
                    semester.getRegistrationClosesAt(), semester.getDropDeadline(), open && active, dropOpen,
                    maxCredits, registeredCredits, eligible, registered));
        }
        return new RegistrationOverview(active, answer);
    }

    /**
     * Registers the student for every chosen offering, or for none: the first problem answers with a plain message
     * naming the course, and nothing is saved. The answer lists the new course enrollments.
     */
    public RegistrationResult register(Long userAccountId, List<Long> courseOfferingIds) {
        if (new HashSet<>(courseOfferingIds).size() != courseOfferingIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, REPEATED);
        }
        StudentProfile profile = profileOf(userAccountId);
        if (profile.getStatus() != StudentProfileStatus.ACTIVE) {
            throw conflict(PROFILE_INACTIVE);
        }
        Instant now = clock.instant();

        // Load every offering first, so a missing one changes nothing.
        Map<Long, CourseOffering> chosen = new TreeMap<>();
        for (Long id : courseOfferingIds) {
            chosen.put(id, offerings.findWithContextById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, OFFERING_MISSING)));
        }

        // Lock the student's semester enrollments, then the offerings, each in ID order, as admin enrollment does.
        Map<Long, SemesterEnrollment> termBySemester = new TreeMap<>();
        for (CourseOffering offering : chosen.values()) {
            Long semesterId = offering.getSemester().getId();
            if (!termBySemester.containsKey(semesterId)) {
                SemesterEnrollment term = semesterEnrollments
                        .findByStudentProfileIdAndSemesterId(profile.getId(), semesterId)
                        .filter(found -> found.getStatus() == SemesterEnrollmentStatus.ENROLLED)
                        .orElseThrow(() -> conflict(name(offering) + " is not offered to a semester you are enrolled in."));
                termBySemester.put(semesterId, term);
            }
        }
        termBySemester.values().stream().map(SemesterEnrollment::getId).sorted()
                .forEach(semesterEnrollments::findByIdForUpdate);
        chosen.keySet().forEach(offerings::findByIdForUpdate);

        Map<Long, BigDecimal> addedCredits = new TreeMap<>();
        for (CourseOffering offering : chosen.values()) {
            Semester semester = offering.getSemester();
            SemesterEnrollment term = termBySemester.get(semester.getId());
            if (offering.getStatus() == CourseOfferingStatus.CLOSED
                    || offering.getRegistrationStatus() != OfferingRegistrationStatus.OPEN) {
                throw conflict("Registration for " + name(offering) + " is not open.");
            }
            if (semester.getStatus() == SemesterStatus.CLOSED || !semester.isRegistrationOpenAt(now)) {
                throw conflict("Registration for semester " + semester.getSemesterNumber() + " is not open now.");
            }
            if (!sectionMatches(offering, term)) {
                throw conflict(name(offering) + " is for section " + offering.getSection().getCode()
                        + (term.getSection() == null ? ", and you are not placed in a section."
                                : ", and you are in section " + term.getSection().getCode() + "."));
            }
            courseEnrollments.findBySemesterEnrollmentIdAndCourseOfferingId(term.getId(), offering.getId())
                    .ifPresent(existing -> {
                        throw conflict(existing.getStatus() == CourseEnrollmentStatus.ENROLLED
                                ? "You are already registered for " + name(offering) + "."
                                : "You dropped or ended " + name(offering) + " earlier, so it cannot be taken again.");
                    });
            Integer capacity = offering.getCapacity();
            if (capacity != null && courseEnrollments.countEnrolledInOfferingForShare(offering.getId()) >= capacity) {
                throw conflict(OfferingCapacity.fullMessage(offering));
            }
            addedCredits.merge(term.getId(), offering.getCourse().getCredits(), BigDecimal::add);
        }

        for (SemesterEnrollment term : termBySemester.values()) {
            BigDecimal limit = term.getSemester().getProgram().getMaxCreditsPerSemester();
            if (limit == null) {
                continue;
            }
            BigDecimal current = enrolledCredits(courseEnrollments
                    .findAllWithOfferingBySemesterEnrollmentIdIn(List.of(term.getId())));
            BigDecimal total = current.add(addedCredits.getOrDefault(term.getId(), BigDecimal.ZERO));
            if (total.compareTo(limit) > 0) {
                throw conflict("This would bring you to " + plain(total) + " credits in semester "
                        + term.getSemester().getSemesterNumber() + ", over the limit of " + plain(limit) + ".");
            }
        }

        List<CourseEnrollment> created = new ArrayList<>();
        try {
            for (CourseOffering offering : chosen.values()) {
                created.add(courseEnrollments.saveAndFlush(
                        new CourseEnrollment(termBySemester.get(offering.getSemester().getId()), offering)));
            }
        } catch (DataIntegrityViolationException clash) {
            throw conflict("You are already registered for one of these courses.");
        }
        Map<Long, List<String>> names = facultyNames(chosen.keySet());
        List<RegisteredCourse> registered = created.stream()
                .map(enrollment -> registeredCourse(enrollment, names, false))
                .toList();
        BigDecimal credits = created.stream().map(enrollment -> enrollment.getCourseOffering().getCourse().getCredits())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RegistrationResult(registered, registered.size(), credits);
    }

    /**
     * Drops one of the student's own enrolled courses before the semester's drop deadline. Another student's
     * enrollment answers the same 404 as a missing one.
     */
    public RegisteredCourse drop(Long userAccountId, Long courseEnrollmentId) {
        CourseEnrollment enrollment = courseEnrollments.findByIdForUpdate(courseEnrollmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ENROLLMENT_MISSING));
        StudentProfile owner = enrollment.getSemesterEnrollment().getStudentProfile();
        if (!Objects.equals(owner.getUserAccount().getId(), userAccountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ENROLLMENT_MISSING);
        }
        if (owner.getStatus() != StudentProfileStatus.ACTIVE) {
            throw conflict(PROFILE_INACTIVE);
        }
        if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
            throw conflict(NOT_ENROLLED);
        }
        Semester semester = enrollment.getCourseOffering().getSemester();
        Instant now = clock.instant();
        if (semester.getDropDeadline() == null) {
            throw conflict(NO_DROPPING);
        }
        if (!semester.isDropAllowedAt(now)) {
            throw conflict(DROP_PASSED);
        }
        enrollment.drop(now);
        CourseEnrollment saved = courseEnrollments.saveAndFlush(enrollment);
        Long offeringId = saved.getCourseOffering().getId();
        return registeredCourse(saved, facultyNames(List.of(offeringId)), false);
    }

    private StudentProfile profileOf(Long userAccountId) {
        return profiles.findWithContextByUserAccountId(userAccountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NO_PROFILE));
    }

    /**
     * The semesters the student may register in: their enrolled semester enrollments of semesters that are not closed,
     * and of those, only the ones in the college's current session when any is.
     */
    private List<SemesterEnrollment> registrationTerms(StudentProfile profile) {
        List<SemesterEnrollment> open = semesterEnrollments.findAllWithContextByStudentProfileId(profile.getId()).stream()
                .filter(term -> term.getStatus() == SemesterEnrollmentStatus.ENROLLED)
                .filter(term -> term.getSemester().getStatus() != SemesterStatus.CLOSED)
                .toList();
        List<SemesterEnrollment> current = open.stream()
                .filter(term -> term.getSemester().getAcademicSession().isCurrent())
                .toList();
        return current.isEmpty() ? open : current;
    }

    private static boolean isOpenTo(CourseOffering offering, SemesterEnrollment term) {
        return offering.getStatus() != CourseOfferingStatus.CLOSED
                && offering.getRegistrationStatus() == OfferingRegistrationStatus.OPEN
                && sectionMatches(offering, term);
    }

    private static boolean sectionMatches(CourseOffering offering, SemesterEnrollment term) {
        return offering.getSection() == null || (term.getSection() != null
                && term.getSection().getId().equals(offering.getSection().getId()));
    }

    private static String reason(CourseOffering offering, CourseEnrollment mine, boolean active, boolean open,
            Integer free, BigDecimal registeredCredits, BigDecimal maxCredits) {
        if (mine != null) {
            return mine.getStatus() == CourseEnrollmentStatus.ENROLLED ? REASON_REGISTERED : REASON_ENDED;
        }
        if (!active) {
            return REASON_PROFILE;
        }
        if (!open) {
            return REASON_CLOSED;
        }
        if (free != null && free <= 0) {
            return REASON_FULL;
        }
        if (maxCredits != null && registeredCredits.add(offering.getCourse().getCredits()).compareTo(maxCredits) > 0) {
            return REASON_CREDITS;
        }
        return null;
    }

    private static Integer freePlaces(CourseOffering offering, Map<Long, Long> enrolledCounts) {
        Integer capacity = offering.getCapacity();
        if (capacity == null) {
            return null;
        }
        long taken = enrolledCounts.getOrDefault(offering.getId(), 0L);
        return (int) Math.max(0, capacity - taken);
    }

    private Map<Long, Long> enrolledCounts(Collection<Long> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        return courseEnrollments.countByOfferingAndStatus(offeringIds).stream()
                .filter(count -> count.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .collect(Collectors.toMap(CourseEnrollmentRepository.OfferingStatusCount::getCourseOfferingId,
                        CourseEnrollmentRepository.OfferingStatusCount::getTotal));
    }

    /** The names of the faculty actively teaching each offering, primary first; members without a name are left out. */
    private Map<Long, List<String>> facultyNames(Collection<Long> offeringIds) {
        Map<Long, List<String>> names = new LinkedHashMap<>();
        faculty.byOffering(offeringIds).forEach((offeringId, members) -> names.put(offeringId, members.stream()
                .filter(member -> "ACTIVE".equals(member.status()) && member.name() != null)
                .sorted(Comparator.comparing((OfferingFaculty member) -> !"PRIMARY".equals(member.role())))
                .map(OfferingFaculty::name)
                .toList()));
        return names;
    }

    private static BigDecimal enrolledCredits(List<CourseEnrollment> enrollments) {
        return enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .map(enrollment -> enrollment.getCourseOffering().getCourse().getCredits())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static RegisteredCourse registeredCourse(CourseEnrollment enrollment, Map<Long, List<String>> names,
            boolean dropOpen) {
        CourseOffering offering = enrollment.getCourseOffering();
        return new RegisteredCourse(enrollment.getId(), offering.getId(), offering.getCourse().getCode(),
                offering.getCourse().getName(), offering.getCourse().getCredits(), sectionCode(offering),
                names.getOrDefault(offering.getId(), List.of()), enrollment.getStatus().name(), enrollment.getCreatedAt(),
                enrollment.getDroppedAt(), dropOpen && enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED);
    }

    private static String sectionCode(CourseOffering offering) {
        return offering.getSection() == null ? null : offering.getSection().getCode();
    }

    private static String name(CourseOffering offering) {
        return offering.getCourse().getCode();
    }

    private static String plain(BigDecimal credits) {
        return credits.stripTrailingZeros().toPlainString();
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
