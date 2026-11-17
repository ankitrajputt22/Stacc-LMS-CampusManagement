package com.stacc.backend.devdata;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.curriculum.DegreeProgressService;
import com.stacc.backend.academic.curriculum.DegreeProgressService.Requirement;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.request.StudentRequestService;
import com.stacc.backend.academic.exam.ExamService;
import com.stacc.backend.fees.FeePayment;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.mess.Meal;
import com.stacc.backend.hostel.mess.MessService;
import com.stacc.backend.hostel.notice.HostelNoticeService;
import com.stacc.backend.fees.FeeService;
import com.stacc.backend.fees.FeeStructureItem;
import com.stacc.backend.academic.attendance.api.StudentAttendanceQueryService;
import com.stacc.backend.academic.result.AcademicStandingService;
import com.stacc.backend.academic.timetable.ClassTimetableSlot.Kind;
import com.stacc.backend.academic.timetable.TimetableService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ERP records of the local college (v0.16), after the other seeders: weekly timetables for the current CSE and ECE
 * semesters, degree requirements for both programs, and Aarav's mailing address and emergency contacts. Like the
 * other seeders it only finds or creates, through the feature services, and skips what already exists.
 */
@Component
@Profile(LocalTestDataSeeder.PROFILE)
@Order(30)
public class LocalErpSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalErpSeeder.class);

    private record Slot(String course, DayOfWeek day, int startHour, int hours, String room, Kind kind) {
    }

    private static final List<Slot> CSE_WEEK = List.of(
            new Slot("CS301", DayOfWeek.MONDAY, 9, 1, "CSE-204", Kind.LECTURE),
            new Slot("CS301", DayOfWeek.THURSDAY, 9, 1, "CSE-204", Kind.LECTURE),
            new Slot("CS302", DayOfWeek.MONDAY, 10, 1, "CSE-205", Kind.LECTURE),
            new Slot("CS302", DayOfWeek.WEDNESDAY, 10, 1, "CSE-205", Kind.LECTURE),
            new Slot("CS302", DayOfWeek.FRIDAY, 10, 1, "CSE-205", Kind.LECTURE),
            new Slot("CS303", DayOfWeek.TUESDAY, 9, 1, "CSE-202", Kind.LECTURE),
            new Slot("CS303", DayOfWeek.THURSDAY, 10, 1, "CSE-202", Kind.LECTURE),
            new Slot("CS303", DayOfWeek.SATURDAY, 9, 1, "CSE-202", Kind.LECTURE),
            new Slot("CS304", DayOfWeek.MONDAY, 11, 1, "CSE-203", Kind.TUTORIAL),
            new Slot("CS304", DayOfWeek.WEDNESDAY, 11, 1, "CSE-203", Kind.TUTORIAL),
            new Slot("CS304", DayOfWeek.FRIDAY, 11, 1, "CSE-203", Kind.TUTORIAL),
            new Slot("CS305", DayOfWeek.TUESDAY, 14, 2, "LAB-02", Kind.LAB),
            new Slot("CS305", DayOfWeek.FRIDAY, 14, 2, "LAB-02", Kind.LAB));

    private static final List<Slot> ECE_WEEK = List.of(
            new Slot("EC301", DayOfWeek.MONDAY, 9, 1, "ECE-101", Kind.LECTURE),
            new Slot("EC301", DayOfWeek.WEDNESDAY, 9, 1, "ECE-101", Kind.LECTURE),
            new Slot("EC302", DayOfWeek.TUESDAY, 10, 1, "ECE-102", Kind.LECTURE),
            new Slot("EC302", DayOfWeek.THURSDAY, 10, 1, "ECE-102", Kind.LECTURE),
            new Slot("EC304", DayOfWeek.FRIDAY, 11, 1, "ECE-103", Kind.TUTORIAL));

    private static final List<Requirement> BTECH = List.of(
            new Requirement(CourseType.CORE, new BigDecimal("96")),
            new Requirement(CourseType.ELECTIVE, new BigDecimal("42")),
            new Requirement(CourseType.LAB, new BigDecimal("12")),
            new Requirement(CourseType.PROJECT, new BigDecimal("10")));

    private final Environment environment;
    private final TransactionTemplate transaction;
    private final UserAccountRepository accounts;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseOfferingRepository offerings;
    private final TimetableService timetable;
    private final DegreeProgressService degree;
    private final PersonProfileService people;
    private final StudentRequestService requests;
    private final StudentAttendanceQueryService attendance;
    private final AcademicStandingService standing;
    private final ExamService exams;
    private final FeeService fees;
    private final HostelRepository hostels;
    private final MessService mess;
    private final HostelNoticeService hostelNotices;

    public LocalErpSeeder(Environment environment, TransactionTemplate transaction, UserAccountRepository accounts,
            CourseEnrollmentRepository courseEnrollments, CourseOfferingRepository offerings, TimetableService timetable,
            DegreeProgressService degree, PersonProfileService people, StudentRequestService requests,
            StudentAttendanceQueryService attendance, AcademicStandingService standing, ExamService exams, FeeService fees,
            HostelRepository hostels, MessService mess, HostelNoticeService hostelNotices) {
        this.environment = environment;
        this.transaction = transaction;
        this.accounts = accounts;
        this.courseEnrollments = courseEnrollments;
        this.offerings = offerings;
        this.timetable = timetable;
        this.degree = degree;
        this.people = people;
        this.requests = requests;
        this.attendance = attendance;
        this.standing = standing;
        this.exams = exams;
        this.fees = fees;
        this.hostels = hostels;
        this.mess = mess;
        this.hostelNotices = hostelNotices;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        LocalTestDataSeeder.checkedPassword(environment);
        transaction.executeWithoutResult(status -> week("cse24001", CSE_WEEK));
        transaction.executeWithoutResult(status -> week("ece25001", ECE_WEEK));
        transaction.executeWithoutResult(status -> requirements("cse24001"));
        transaction.executeWithoutResult(status -> requirements("ece25001"));
        transaction.executeWithoutResult(status -> contacts());
        transaction.executeWithoutResult(status -> requests());
        transaction.executeWithoutResult(status -> examSession("cse24001", "B-12"));
        transaction.executeWithoutResult(status -> examSession("ece25001", "ECE-HALL-1"));
        transaction.executeWithoutResult(status -> feeStructure("cse24001"));
        transaction.executeWithoutResult(status -> feeStructure("ece25001"));
        transaction.executeWithoutResult(status -> aaravsFees());
        transaction.executeWithoutResult(status -> hostelLife());
        LOGGER.info("Local ERP records ({} profile) ready.", LocalTestDataSeeder.PROFILE);
    }

    // The week of the semester the student is enrolled in now, added only when that semester has no classes yet.
    private void week(String loginId, List<Slot> week) {
        accounts.findByLoginId(loginId).ifPresent(account -> {
            var enrolled = courseEnrollments.findAllWithStudentByUserAccountId(account.getId()).stream()
                    .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED).findFirst();
            enrolled.ifPresent(enrollment -> {
                Long semesterId = enrollment.getCourseOffering().getSemester().getId();
                if (!timetable.forSemester(semesterId).isEmpty()) {
                    return;
                }
                Map<String, CourseOffering> byCode = offerings.findAllWithContext(semesterId, null, null, null, Pageable.unpaged())
                        .stream().filter(offering -> offering.getSection() == null)
                        .collect(Collectors.toMap(offering -> offering.getCourse().getCode(), Function.identity(), (a, b) -> a));
                for (Slot slot : week) {
                    CourseOffering offering = byCode.get(slot.course());
                    if (offering == null) {
                        continue;
                    }
                    try {
                        timetable.add(offering.getId(), slot.day(), LocalTime.of(slot.startHour(), 0),
                                LocalTime.of(slot.startHour() + slot.hours(), 0), slot.room(), slot.kind());
                    } catch (ApiException clash) {
                        LOGGER.debug("Skipped a demo class: {}", clash.getMessage());
                    }
                }
            });
        });
    }

    private void requirements(String loginId) {
        accounts.findByLoginId(loginId).ifPresent(account -> {
            try {
                var program = degree.mine(account.getId());
                if (program.groups().isEmpty()) {
                    // The program code alone does not say which row; the student's own profile names it.
                    degree.set(programIdOf(account), BTECH);
                }
            } catch (ApiException missing) {
                LOGGER.debug("No student profile for {}", loginId);
            }
        });
    }

    private Long programIdOf(UserAccount account) {
        return courseEnrollments.findAllWithStudentByUserAccountId(account.getId()).stream().findFirst()
                .map(enrollment -> enrollment.getSemesterEnrollment().getStudentProfile().getProgram().getId())
                .orElseThrow(() -> new IllegalStateException("No enrollment for " + account.getLoginId()));
    }

    private void contacts() {
        accounts.findByLoginId("cse24001").ifPresent(account -> {
            var profile = people.myProfile(account.getId());
            if (!profile.recorded()) {
                return;
            }
            if (profile.mailingAddress() == null) {
                people.changeMyMailingAddress(account.getId(), "12 College Road, Shivajinagar\nPune 411005, Maharashtra");
            }
            if (profile.emergencyContacts().isEmpty()) {
                people.addMyEmergencyContact(account.getId(), "Priya Sharma", "Mother", "+91 98220 41736");
                people.addMyEmergencyContact(account.getId(), "Rajesh Sharma", "Father", "+91 98220 41737");
            }
        });
    }

    // A few requests waiting for the office: leave and a revaluation from Aarav, a correction from a student who often
    // misses class. Only when the student has none yet.
    private void requests() {
        accounts.findByLoginId("cse24001").ifPresent(account -> {
            if (!requests.mine(account.getId()).isEmpty()) {
                return;
            }
            var today = java.time.LocalDate.now();
            requests.medicalLeave(account.getId(), today.plusDays(2), today.plusDays(3),
                    "I have a fever and the doctor has advised two days of rest.");
            standing.standing(account.getId()).semesters().stream().flatMap(semester -> semester.courses().stream())
                    .filter(course -> course.courseCode().equals("CS201")).findFirst()
                    .ifPresent(course -> requests.revaluation(account.getId(), course.courseResultId(),
                            "I expected higher marks in the second long answer; please check it again."));
        });
        accounts.findByLoginId("cse25008").ifPresent(account -> {
            if (!requests.mine(account.getId()).isEmpty()) {
                return;
            }
            attendance.getMyAttendance(account.getId()).courses().stream()
                    .flatMap(course -> course.sessions().stream())
                    .filter(session -> session.status().equals("ABSENT")).findFirst()
                    .ifPresent(session -> requests.correction(account.getId(), session.attendanceRecordId(),
                            "I was in class for the whole lecture but was marked absent."));
        });
    }

    // The end-semester exams of the semester the student is enrolled in now: one exam every other weekday from 7 Dec,
    // 10:00 to 13:00 (labs to 12:00), every student seated, published. Only when the semester has no exam session.
    private void examSession(String loginId, String hall) {
        accounts.findByLoginId(loginId).ifPresent(account -> courseEnrollments.findAllWithStudentByUserAccountId(account.getId()).stream()
                .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED).findFirst()
                .ifPresent(enrollment -> {
                    Long semesterId = enrollment.getCourseOffering().getSemester().getId();
                    if (!exams.forSemester(semesterId).isEmpty()) {
                        return;
                    }
                    Long sessionId = exams.createSession(semesterId, "End Semester Examinations — December 2026").examSessionId();
                    var day = java.time.LocalDate.of(2026, 12, 7);
                    var semesterOfferings = offerings.findAllWithContext(semesterId, null, null, null, Pageable.unpaged()).stream()
                            .filter(offering -> offering.getSection() == null)
                            .filter(offering -> offering.getStatus() != com.stacc.backend.academic.offering.CourseOfferingStatus.CLOSED)
                            .toList();
                    for (CourseOffering offering : semesterOfferings) {
                        boolean lab = offering.getCourse().getCourseType() == CourseType.LAB;
                        var exam = exams.addExam(sessionId, offering.getId(), day, LocalTime.of(10, 0), LocalTime.of(lab ? 12 : 13, 0),
                                lab ? "LAB-02" : hall, 60);
                        exams.seatPlan(exam.slotId());
                        day = day.plusDays(day.getDayOfWeek() == DayOfWeek.FRIDAY ? 3 : 2);
                    }
                    exams.publish(sessionId);
                }));
    }

    private static final List<FeeService.Item> BTECH_FEES = List.of(
            new FeeService.Item(FeeStructureItem.Head.TUITION, new BigDecimal("60000")),
            new FeeService.Item(FeeStructureItem.Head.EXAMINATION, new BigDecimal("4000")),
            new FeeService.Item(FeeStructureItem.Head.LIBRARY, new BigDecimal("2000")),
            new FeeService.Item(FeeStructureItem.Head.LABORATORY, new BigDecimal("5000")),
            new FeeService.Item(FeeStructureItem.Head.HOSTEL, new BigDecimal("30000")),
            new FeeService.Item(FeeStructureItem.Head.MESS, new BigDecimal("11500")));

    // The program's fees for the session the student is enrolled in now, due on 15 Dec 2026, when none are set yet.
    private void feeStructure(String loginId) {
        accounts.findByLoginId(loginId).ifPresent(account -> courseEnrollments.findAllWithStudentByUserAccountId(account.getId()).stream()
                .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED).findFirst()
                .ifPresent(enrollment -> {
                    Long programId = enrollment.getSemesterEnrollment().getStudentProfile().getProgram().getId();
                    Long sessionId = enrollment.getCourseOffering().getSemester().getAcademicSession().getId();
                    if (fees.structure(programId, sessionId).items().isEmpty()) {
                        fees.setStructure(programId, sessionId, java.time.LocalDate.of(2026, 12, 15), BTECH_FEES);
                    }
                }));
    }

    // Aarav has paid three instalments and holds a merit scholarship, leaving the rest due on 15 Dec.
    private void aaravsFees() {
        Long office = accounts.findByLoginId("adm1001").map(UserAccount::getId).orElse(null);
        accounts.findByLoginId("cse24001").ifPresent(account -> {
            if (office == null || !fees.mine(account.getId()).payments().isEmpty()) {
                return;
            }
            fees.recordPayment(office, "cse24001", new BigDecimal("40000"), FeePayment.Mode.BANK_TRANSFER, "BANK-2026-0841",
                    java.time.LocalDate.of(2026, 8, 10));
            fees.recordPayment(office, "cse24001", new BigDecimal("20000"), FeePayment.Mode.UPI, "UPI-2026-0976",
                    java.time.LocalDate.of(2026, 9, 1));
            fees.recordPayment(office, "cse24001", new BigDecimal("10000"), FeePayment.Mode.UPI, "UPI-2026-1102",
                    java.time.LocalDate.of(2026, 11, 2));
            Long scholarship = fees.applyForScholarship(account.getId(), "Merit Scholarship", new BigDecimal("10000"),
                    "Top 5% of the department in the first year.").adjustmentId();
            fees.decide(office, scholarship, true, "Awarded for 2026-27.");
        });
    }

    private static final String[][] MENU = {
            {"Poha, banana, tea", "Rajma chawal, roti, salad", "Samosa, tea", "Dal, rice, roti and vegetables"},
            {"Idli, sambar, coffee", "Chole, rice, roti, curd", "Biscuits, tea", "Paneer butter masala, roti, rice"},
            {"Upma, boiled egg, tea", "Veg biryani, raita", "Pakora, tea", "Dal tadka, jeera rice, roti"},
            {"Paratha, curd, tea", "Kadhi chawal, roti", "Sandwich, tea", "Mixed vegetables, dal, roti, rice"},
            {"Dosa, chutney, coffee", "Sambar rice, poriyal", "Fruit, tea", "Egg curry or paneer, rice, roti"},
            {"Puri, aloo sabzi, tea", "Pulao, dal, salad", "Bhel puri, tea", "Chicken or soya curry, rice, roti"},
            {"Bread, omelette, tea", "Special thali", "Cake, tea", "Khichdi, papad, pickle"}};

    // Main Hostel's weekly menu and two published notices, set as the hostel office would, when none exist yet.
    private void hostelLife() {
        Long office = accounts.findByLoginId("adm1001").map(UserAccount::getId).orElse(null);
        hostels.findByCode("MAIN").ifPresent(hostel -> {
            if (office == null) {
                return;
            }
            var manager = new MessService.Manager(office, true);
            if (mess.forManager(manager, hostel.getId(), java.time.LocalDate.now()).template().isEmpty()) {
                List<MessService.TemplateLine> lines = new java.util.ArrayList<>();
                Meal[] meals = {Meal.BREAKFAST, Meal.LUNCH, Meal.SNACKS, Meal.DINNER};
                for (int day = 1; day <= 7; day++) {
                    for (int meal = 0; meal < meals.length; meal++) {
                        lines.add(new MessService.TemplateLine(day, meals[meal], MENU[day - 1][meal]));
                    }
                }
                mess.setTemplate(manager, hostel.getId(), lines);
            }
            var notices = new HostelNoticeService.Manager(office, true);
            if (hostelNotices.forHostel(notices, hostel.getId()).isEmpty()) {
                Long water = hostelNotices.add(notices, hostel.getId(), "A", "Water supply maintenance",
                        "Water will be off in Block A on Tuesday from 10:00 to 12:00 while the tanks are cleaned.", false).noticeId();
                hostelNotices.publish(notices, water);
                Long drill = hostelNotices.add(notices, hostel.getId(), null, "Emergency assembly drill",
                        "When the alarm sounds, assemble in the courtyard and wait for the hostel staff.", true).noticeId();
                hostelNotices.publish(notices, drill);
            }
        });
    }
}
