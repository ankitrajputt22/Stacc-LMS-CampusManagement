package com.stacc.backend.devdata;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.OfferingRegistrationStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.teaching.TeachingRole;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsTextFormat;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;

/**
 * A fuller college for trying Stacc by hand, after the main local data: a second department (Electronics and
 * Communication Engineering) with its head, teachers, courses, and fifteen students, and twelve more Computer Science
 * students in their own section, with coursework, hand-ins and grades, attendance with realistic gaps, published
 * results of the previous semester, helpdesk tickets, campus requests, and hostel applications waiting for a decision.
 * <p>
 * Login IDs are college IDs ({@code cse25004}, {@code ece25001}) and employee IDs ({@code emp2011}); every account
 * uses the same test password. Like the main seeder it only finds or creates, through the main seeder's helpers and so
 * through the feature services, and never changes or deletes what exists. Attendance marks and marks are worked out
 * from each student's place in the list, so a second run produces the same records.
 */
final class LocalCollegePopulation {

    record Person(String loginId, String name) {
    }

    /** New Computer Science students, in section C of semester 3. */
    static final List<Person> CSE_STUDENTS = List.of(
            new Person("cse25004", "Ishaan Kulkarni"), new Person("cse25005", "Ananya Reddy"),
            new Person("cse25006", "Vihaan Joshi"), new Person("cse25007", "Saanvi Desai"),
            new Person("cse25008", "Kabir Malhotra"), new Person("cse25009", "Myra Chatterjee"),
            new Person("cse25010", "Reyansh Pillai"), new Person("cse25011", "Aditi Bhatt"),
            new Person("cse25012", "Arnav Sinha"), new Person("cse25013", "Kiara Fernandes"),
            new Person("cse25014", "Dhruv Saxena"), new Person("cse25015", "Tara Krishnan"));

    /** Electronics and Communication students: the first eight in section A, the rest in section B. */
    static final List<Person> ECE_STUDENTS = List.of(
            new Person("ece25001", "Nikhil Agarwal"), new Person("ece25002", "Pooja Hegde"),
            new Person("ece25003", "Rahul Bose"), new Person("ece25004", "Sneha Kapoor"),
            new Person("ece25005", "Varun Mehta"), new Person("ece25006", "Riya Banerjee"),
            new Person("ece25007", "Siddharth Kamath"), new Person("ece25008", "Meghna Dutta"),
            new Person("ece25009", "Karan Thakur"), new Person("ece25010", "Nisha Pandey"),
            new Person("ece25011", "Aman Choudhary"), new Person("ece25012", "Shreya Mishra"),
            new Person("ece25013", "Yash Jain"), new Person("ece25014", "Priya Nambiar"),
            new Person("ece25015", "Harsh Vardhan"));

    static final Person CSE_CO_TEACHER = new Person("emp2011", "Anil Deshpande");
    static final Person CSE_TEACHER = new Person("emp2012", "Farah Siddiqui");
    static final Person ECE_HEAD = new Person("emp3001", "Prakash Raman");
    static final Person SIGNALS_TEACHER = new Person("emp3002", "Divya Srinivasan");
    static final Person ANALOG_TEACHER = new Person("emp3003", "Manoj Tiwari");
    static final Person DIGITAL_TEACHER = new Person("emp3004", "Kavya Nair");

    /** Students who often miss class, so low attendance can be tried. */
    private static final Set<String> OFTEN_ABSENT = Set.of("cse25008", "ece25005", "ece25012");
    private static final String[] GRADES = {"A+", "A", "B+", "B", "A", "C", "B+", "A", "B", "A+", "B", "C", "A", "B+", "B"};

    private final LocalTestDataSeeder seed;
    private final Clock clock;

    LocalCollegePopulation(LocalTestDataSeeder seed, Clock clock) {
        this.seed = seed;
        this.clock = clock;
    }

    void seed(String password) {
        LocalDate today = LocalDate.now(clock);
        int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Instant registrationOpened = now.minus(1, ChronoUnit.DAYS);

        UserAccount admin = seed.account("admin1", password, RoleName.ADMIN);
        AcademicSession previous = seed.session(startYear - 1, AcademicSession::close);
        AcademicSession current = seed.session(startYear, session -> {
            session.markActive();
            session.makeCurrent();
        });

        computerScience(password, today, now, previous, current);
        electronics(password, today, now, registrationOpened, admin, previous, current);
    }

    // --- Computer Science: twelve more students in section C, and two more teachers.

    private void computerScience(String password, LocalDate today, Instant now, AcademicSession previous,
            AcademicSession current) {
        Department cse = seed.department("CSE", "Computer Science and Engineering");
        Program btech = seed.program(cse, "BTECH-CSE", "Bachelor of Technology in Computer Science and Engineering", 8, "24.00");
        Semester previousSecond = seed.semester(btech, previous, 2, Semester::close);
        Semester third = seed.semester(btech, current, 3, Semester::markActive);
        CourseOffering cs201 = seed.offering(seed.course(cse, "CS201", "Object-Oriented Programming", "4.00"), previousSecond,
                CourseOffering::close);
        CourseOffering cs301 = seed.offering(seed.course(cse, "CS301", "Data Structures", "4.00"), third, CourseOffering::markActive);
        CourseOffering cs302 = seed.offering(seed.course(cse, "CS302", "Database Systems", "4.00"), third, CourseOffering::markActive);
        CourseOffering cs304 = seed.offering(seed.course(cse, "CS304", "Discrete Mathematics", "3.00"), third,
                CourseOffering::markActive);
        Section sectionC = seed.section(third, "C", 60);

        UserAccount faculty1 = seed.account("faculty1", password, RoleName.FACULTY);
        FacultyProfile coTeacher = teacher(password, CSE_CO_TEACHER, cse);
        FacultyProfile discreteTeacher = teacher(password, CSE_TEACHER, cse);
        seed.teaching(coTeacher, cs301, TeachingRole.CO_FACULTY);
        seed.teaching(discreteTeacher, cs304, TeachingRole.CO_FACULTY);

        List<UserAccount> students = new ArrayList<>();
        List<CourseEnrollment> inDataStructures = new ArrayList<>();
        for (int index = 0; index < CSE_STUDENTS.size(); index++) {
            Person person = CSE_STUDENTS.get(index);
            UserAccount account = student(password, person);
            StudentProfile profile = seed.studentProfile(account, btech, previous, "25CSE" + person.loginId().substring(5));
            // Last semester: Object-Oriented Programming, completed with a published result.
            SemesterEnrollment last = seed.semesterEnrollment(profile, previousSecond, SemesterEnrollment::markCompleted);
            CourseEnrollment oop = seed.courseEnrollment(last, cs201, CourseEnrollment::markCompleted);
            seed.result(faculty1, cs201, oop, String.valueOf(58 + (index * 7) % 40), "100", GRADES[index % GRADES.length],
                    null, true);
            // This semester: section C, three courses.
            SemesterEnrollment now3 = seed.semesterEnrollment(profile, third, enrollment -> enrollment.placeIn(sectionC));
            inDataStructures.add(seed.courseEnrollment(now3, cs301, enrollment -> { }));
            seed.courseEnrollment(now3, cs302, enrollment -> { });
            seed.courseEnrollment(now3, cs304, enrollment -> { });
            students.add(account);
        }

        // Coursework in Data Structures: most handed in the warm-up (late, its deadline has passed) and were graded;
        // a few have started the linked list implementation.
        Long lms301 = seed.lmsCourse(faculty1, cs301, LmsCourseStatus.PUBLISHED);
        LmsAssignment warmUp = seed.assignment(faculty1, lms301, "Array warm-up exercises",
                "Solve problems 1 to 5 from the arrays worksheet.", LocalTestDataSeeder.deadline(today, -5), "20",
                LmsContentStatus.PUBLISHED);
        LmsAssignment linkedList = seed.assignment(faculty1, lms301, "Linked list implementation",
                "Implement a singly linked list with insert, delete, and search.", LocalTestDataSeeder.deadline(today, 9), "50",
                LmsContentStatus.PUBLISHED);
        for (int index = 0; index < students.size(); index++) {
            UserAccount student = students.get(index);
            CourseEnrollment enrollment = inDataStructures.get(index);
            if (index % 6 != 5) {
                seed.submit(student, lms301, warmUp, enrollment, WARM_UP_ANSWERS[index % WARM_UP_ANSWERS.length]);
                if (index % 4 != 3) {
                    seed.grade(faculty1, warmUp, enrollment, 1, String.valueOf(9 + (index * 5) % 12),
                            WARM_UP_FEEDBACK[index % WARM_UP_FEEDBACK.length]);
                }
            }
            if (index % 3 == 0) {
                seed.submit(student, lms301, linkedList, enrollment,
                        "Insert and search are done; delete handles the head and the tail. Tests are in LinkedListTest.");
            }
        }

        // Attendance: two more finalized classes with the whole class, so percentages are worth reading.
        seed.attendance(faculty1, cs301, "Queues and circular buffers", now.minus(4, ChronoUnit.DAYS),
                loginId -> mark(loginId, 1), true);
        seed.attendance(faculty1, cs301, "Recursion and the call stack", now.minus(3, ChronoUnit.DAYS),
                loginId -> mark(loginId, 2), true);
    }

    // --- Electronics and Communication Engineering: a whole department.

    private void electronics(String password, LocalDate today, Instant now, Instant registrationOpened, UserAccount admin,
            AcademicSession previous, AcademicSession current) {
        Department ece = seed.department("ECE", "Electronics and Communication Engineering");
        Program btech = seed.program(ece, "BTECH-ECE", "Bachelor of Technology in Electronics and Communication Engineering", 8,
                "24.00");
        Semester previousSecond = seed.semester(btech, previous, 2, Semester::close);
        Semester third = seed.semester(btech, current, 3, semester -> {
            semester.markActive();
            semester.changeRegistrationPeriod(registrationOpened, registrationOpened.plus(61, ChronoUnit.DAYS),
                    registrationOpened.plus(61, ChronoUnit.DAYS));
        });
        Section sectionA = seed.section(third, "A", 60);
        Section sectionB = seed.section(third, "B", 60);

        Course networkTheory = seed.course(ece, "EC201", "Network Theory", "4.00");
        Course signals = seed.course(ece, "EC301", "Signals and Systems", "4.00");
        Course analog = seed.course(ece, "EC302", "Analog Electronic Circuits", "4.00");
        Course digital = seed.course(ece, "EC303", "Digital Logic Design", "3.00");
        Course fields = seed.course(ece, "EC304", "Electromagnetic Theory", "3.00");
        Course microprocessors = seed.course(ece, "EC305", "Microprocessors and Microcontrollers", "3.00", CourseType.ELECTIVE);

        CourseOffering ec201 = seed.offering(networkTheory, previousSecond, CourseOffering::close);
        CourseOffering ec301 = seed.offering(signals, third, CourseOffering::markActive);
        CourseOffering ec302 = seed.offering(analog, third, CourseOffering::markActive);
        CourseOffering ec303 = seed.offering(digital, third, CourseOffering::markActive);
        CourseOffering ec304 = seed.offering(fields, third, CourseOffering::markActive);
        CourseOffering ec305 = seed.offering(microprocessors, third, offering -> {
            offering.markActive();
            offering.changeCapacity(40);
            offering.changeRegistrationStatus(OfferingRegistrationStatus.OPEN);
        });

        UserAccount headAccount = seed.account(ECE_HEAD.loginId(), password, RoleName.FACULTY, RoleName.HOD);
        seed.person(headAccount, ECE_HEAD.name());
        FacultyProfile head = seed.facultyProfile(headAccount, ece);
        seed.departmentHead(admin, ece, headAccount);
        FacultyProfile signalsTeacher = teacher(password, SIGNALS_TEACHER, ece);
        FacultyProfile analogTeacher = teacher(password, ANALOG_TEACHER, ece);
        FacultyProfile digitalTeacher = teacher(password, DIGITAL_TEACHER, ece);
        seed.teaching(analogTeacher, ec201);
        seed.teaching(signalsTeacher, ec301);
        seed.teaching(analogTeacher, ec302);
        seed.teaching(digitalTeacher, ec303);
        seed.teaching(head, ec304);
        seed.teaching(digitalTeacher, ec305);
        UserAccount signalsAccount = seed.account(SIGNALS_TEACHER.loginId(), password, RoleName.FACULTY);
        UserAccount analogAccount = seed.account(ANALOG_TEACHER.loginId(), password, RoleName.FACULTY);
        UserAccount digitalAccount = seed.account(DIGITAL_TEACHER.loginId(), password, RoleName.FACULTY);

        List<UserAccount> students = new ArrayList<>();
        List<StudentProfile> profiles = new ArrayList<>();
        List<CourseEnrollment> inSignals = new ArrayList<>();
        List<CourseEnrollment> inAnalog = new ArrayList<>();
        for (int index = 0; index < ECE_STUDENTS.size(); index++) {
            Person person = ECE_STUDENTS.get(index);
            UserAccount account = student(password, person);
            StudentProfile profile = seed.studentProfile(account, btech, previous, "25ECE" + person.loginId().substring(5));
            SemesterEnrollment last = seed.semesterEnrollment(profile, previousSecond, SemesterEnrollment::markCompleted);
            CourseEnrollment circuits = seed.courseEnrollment(last, ec201, CourseEnrollment::markCompleted);
            seed.result(analogAccount, ec201, circuits, String.valueOf(52 + (index * 9) % 46), "100",
                    GRADES[(index + 3) % GRADES.length], index == 4 ? "Improved steadily after the mid-semester test." : null, true);
            Section section = index < 8 ? sectionA : sectionB;
            SemesterEnrollment now3 = seed.semesterEnrollment(profile, third, enrollment -> enrollment.placeIn(section));
            inSignals.add(seed.courseEnrollment(now3, ec301, enrollment -> { }));
            inAnalog.add(seed.courseEnrollment(now3, ec302, enrollment -> { }));
            seed.courseEnrollment(now3, ec303, enrollment -> { });
            seed.courseEnrollment(now3, ec304, enrollment -> { });
            students.add(account);
            profiles.add(profile);
        }

        // --- Signals and Systems: a published course with two units, a syllabus, an announcement, and assignments.
        Long lms301 = seed.lmsCourse(signalsAccount, ec301, LmsCourseStatus.PUBLISHED);
        Long unit1 = seed.module(signalsAccount, lms301, "Unit 1: Signals and Their Properties",
                "Continuous and discrete signals, and how to classify them.", true);
        seed.topic(signalsAccount, unit1, "Continuous-time and discrete-time signals", """
                A continuous-time signal has a value at every instant, such as the voltage across a capacitor. A \
                discrete-time signal has values only at separate instants, usually equally spaced samples x[n] = x(nT), \
                where T is the sampling period.
                Sampling a continuous signal keeps its information only when the sampling rate is more than twice the \
                highest frequency in the signal (the Nyquist rate). Sampling slower than that causes aliasing: high \
                frequencies appear as lower ones and cannot be separated afterwards.""", true);
        seed.topic(signalsAccount, unit1, "Even, odd, and periodic signals", """
                A signal is even when x(-t) = x(t) and odd when x(-t) = -x(t). Every signal is the sum of an even part, \
                (x(t) + x(-t)) / 2, and an odd part, (x(t) - x(-t)) / 2.
                A signal is periodic when x(t + T) = x(t) for some T > 0; the smallest such T is the fundamental period. \
                The sum of two periodic signals is periodic only when the ratio of their periods is a rational number.""", true);
        seed.topic(signalsAccount, unit1, "Energy and power signals", """
                ## Definitions

                The energy of a signal is $E = \\int_{-\\infty}^{\\infty} |x(t)|^2 \\, dt$ and its average power is \
                $P = \\lim_{T \\to \\infty} \\frac{1}{2T} \\int_{-T}^{T} |x(t)|^2 \\, dt$.

                | Kind | Energy | Power |
                | --- | --- | --- |
                | Energy signal | finite | zero |
                | Power signal | infinite | finite |

                - A single pulse is an energy signal.
                - A sine wave that never stops is a power signal.""", LmsTextFormat.FORMATTED, true);
        Long unit2 = seed.module(signalsAccount, lms301, "Unit 2: Linear Time-Invariant Systems",
                "Impulse response and convolution.", true);
        seed.topic(signalsAccount, unit2, "Impulse response and convolution", """
                A linear time-invariant (LTI) system is described completely by its impulse response h(t): the output \
                for any input x(t) is the convolution y(t) = x(t) * h(t), the integral of x(tau) h(t - tau) over tau.
                To convolve by hand: flip h, shift it by t, multiply it with x, and add up the overlap. Repeat for each t.
                An LTI system is stable when its impulse response is absolutely integrable, and causal when h(t) = 0 \
                for t < 0.""", true);
        seed.topic(signalsAccount, unit2, "Properties of convolution", "Draft: commutative, associative, and distributive laws.",
                false);
        seed.syllabus(signalsAccount, lms301, """
                ## Course overview

                Signals, systems, and the tools to analyse them: classification of signals, LTI systems and \
                convolution, Fourier series, and the Fourier transform.

                ## Assessment

                - Two problem sets: 20%
                - Mid-semester test: 30%
                - End-semester examination: 50%""");
        seed.announcement(signalsAccount, lms301, "Welcome to Signals and Systems",
                "Classes are on Monday, Wednesday, and Friday at 10 am in room ECE-204. The first problem set is already open.",
                true);
        seed.announcement(signalsAccount, lms301, "Tutorial moved to Thursday",
                "This week's tutorial moves to Thursday at 2 pm because of the department seminar.", true);
        LmsAssignment classification = seed.assignment(signalsAccount, lms301, "Signal classification problem set",
                "Classify each of the ten signals on the sheet as even or odd, periodic or not, and energy or power. "
                        + "Show your working.", LocalTestDataSeeder.deadline(today, -4), "20", LmsContentStatus.PUBLISHED);
        LmsAssignment convolution = seed.assignment(signalsAccount, lms301, "Convolution practice",
                "Convolve the three pairs of signals on the sheet by hand and sketch each output.",
                LocalTestDataSeeder.deadline(today, 6), "30", LmsContentStatus.PUBLISHED);
        seed.assignment(signalsAccount, lms301, "Fourier series problem set", "Draft: problems on Fourier series.",
                LocalTestDataSeeder.deadline(today, 20), "20", LmsContentStatus.DRAFT);
        for (int index = 0; index < students.size(); index++) {
            UserAccount student = students.get(index);
            CourseEnrollment enrollment = inSignals.get(index);
            if (index % 5 != 4) {
                seed.submit(student, lms301, classification, enrollment, CLASSIFICATION_ANSWERS[index % CLASSIFICATION_ANSWERS.length]);
                if (index % 3 != 2) {
                    seed.grade(signalsAccount, classification, enrollment, 1, String.valueOf(10 + (index * 3) % 11),
                            CLASSIFICATION_FEEDBACK[index % CLASSIFICATION_FEEDBACK.length]);
                }
            }
            if (index % 4 == 1) {
                seed.submit(student, lms301, convolution, enrollment,
                        "All three convolutions are worked out with sketches; the third uses the flip-and-shift method.");
            }
        }

        // --- Analog Electronic Circuits: published, one unit, an assignment due soon.
        Long lms302 = seed.lmsCourse(analogAccount, ec302, LmsCourseStatus.PUBLISHED);
        Long diodes = seed.module(analogAccount, lms302, "Unit 1: Diodes and Rectifiers",
                "Diode models and rectifier circuits.", true);
        seed.topic(analogAccount, diodes, "Diode models", """
                The ideal diode conducts with no voltage drop when forward biased and blocks completely when reverse \
                biased. The constant-voltage model adds a forward drop of about 0.7 V for silicon. The small-signal \
                model replaces the diode around its operating point with a resistance r_d = V_T / I_D, about 25 mV / I_D \
                at room temperature.""", true);
        seed.topic(analogAccount, diodes, "Half-wave and full-wave rectifiers", """
                A half-wave rectifier passes only one half of each cycle, so its output has a large ripple at the input \
                frequency. A full-wave bridge rectifier uses four diodes to pass both halves, doubling the ripple \
                frequency and halving the capacitor needed for the same ripple. The peak output is the input peak minus \
                two diode drops in a bridge.""", true);
        seed.announcement(analogAccount, lms302, "Lab kits are ready",
                "Collect your breadboard kit from the electronics lab before Wednesday's session.", true);
        seed.assignment(analogAccount, lms302, "Rectifier design",
                "Design a full-wave rectifier with a ripple below 5% for a 12 V, 500 mA load. Submit your calculations.",
                LocalTestDataSeeder.deadline(today, 10), "25", LmsContentStatus.PUBLISHED);

        // --- Digital Logic Design: a draft course its teacher is still preparing. Electromagnetic Theory has none yet.
        Long lms303 = seed.lmsCourse(digitalAccount, ec303, LmsCourseStatus.DRAFT);
        Long logic = seed.module(digitalAccount, lms303, "Unit 1: Number Systems and Boolean Algebra", "Being prepared.", false);
        seed.topic(digitalAccount, logic, "Boolean identities", "De Morgan's laws and simplification.", false);

        // --- Attendance: five finalized classes in Signals and Systems and three in Analog, plus one still open.
        String[] signalsTopics = {"Introduction to signals", "Sampling and aliasing", "Even and odd signals",
            "Energy and power", "Impulse response"};
        for (int index = 0; index < signalsTopics.length; index++) {
            int session = index;
            seed.attendance(signalsAccount, ec301, signalsTopics[index], now.minus(15 - index * 3L, ChronoUnit.DAYS),
                    loginId -> mark(loginId, session), true);
        }
        String[] analogTopics = {"Diode characteristics", "Rectifier circuits", "Filter capacitors"};
        for (int index = 0; index < analogTopics.length; index++) {
            int session = index + 7;
            seed.attendance(analogAccount, ec302, analogTopics[index], now.minus(10 - index * 3L, ChronoUnit.DAYS),
                    loginId -> mark(loginId, session), true);
        }
        seed.attendance(analogAccount, ec302, "Clipper and clamper circuits", now.minus(2, ChronoUnit.HOURS), loginId -> null, false);

        // --- Calendar: department events in the current semester.
        seed.calendarEvent(current, third, "ECE laboratory practical examinations", AcademicCalendarEventCategory.EXAM,
                today.plusDays(18), today.plusDays(20), "Practical examinations for every ECE laboratory course.",
                AcademicCalendarEventStatus.PUBLISHED);
        seed.calendarEvent(current, third, "Guest lecture: 5G radio access networks", AcademicCalendarEventCategory.ACADEMIC,
                today.plusDays(8), today.plusDays(8), "In the seminar hall at 3 pm.", AcademicCalendarEventStatus.PUBLISHED);

        // --- Helpdesk, campus requests, and hostel applications from the department.
        Long lmsCategory = seed.helpdeskCategory("LMS", "LMS and courses", "Course access, content, assignments, and submissions.");
        Long academicCategory = seed.helpdeskCategory("ACADEMIC", "Academic records",
                "Enrollment, attendance, results, and the academic calendar.");
        Long technicalCategory = seed.helpdeskCategory("TECHNICAL", "Technical issues",
                "Sign-in, devices, classrooms, and the campus network.");
        seed.ticket(students.get(2), lmsCategory, "Which sheet does Convolution practice use?",
                "The Convolution practice page says the worksheet is on the sheet, but I cannot find which sheet it means.",
                HelpdeskTicketPriority.NORMAL, List.of(), null, null, HelpdeskTicketStatus.OPEN);
        seed.ticket(students.get(9), academicCategory, "Network Theory result shows the wrong grade",
                "My Network Theory result shows B, but the marks I was told add up to an A.", HelpdeskTicketPriority.HIGH,
                List.of("We have asked the course teacher to check the marks; we will update you here."), admin, admin,
                HelpdeskTicketStatus.IN_PROGRESS);
        seed.ticket(signalsAccount, technicalCategory, "Oscilloscopes in the signals lab need calibration",
                "Three of the oscilloscopes in ECE-Lab-2 show a visible offset on every channel.", HelpdeskTicketPriority.NORMAL,
                List.of("The lab technician calibrated all six oscilloscopes this morning."), admin, admin,
                HelpdeskTicketStatus.RESOLVED);
        seed.campusRequest(students.get(3), "STUDENT", "TRANSPORT", "Bus pass for the north route",
                "I travel from Yelahanka every day and need a pass for the north route bus.");
        seed.campusRequest(students.get(6), "STUDENT", "GENERAL_ADMIN", "Bonafide certificate for an internship",
                "Please issue a bonafide certificate for my summer internship application.");
        for (int index : new int[] {0, 5, 10, 13}) {
            seed.hostelApplication(students.get(index), profiles.get(index), current,
                    index == 5 ? "I would prefer a ground-floor room." : "I live more than 200 km from campus.");
        }
    }

    // --- Shared.

    private UserAccount student(String password, Person person) {
        UserAccount account = seed.account(person.loginId(), password, RoleName.STUDENT);
        seed.person(account, person.name());
        return account;
    }

    private FacultyProfile teacher(String password, Person person, Department department) {
        UserAccount account = seed.account(person.loginId(), password, RoleName.FACULTY);
        seed.person(account, person.name());
        return seed.facultyProfile(account, department);
    }

    /**
     * A student's mark in one class, the same on every run: most attend, students who often miss class are absent from
     * most classes, and everyone else misses about one class in seven.
     */
    static AttendanceStatus mark(String loginId, int session) {
        int seedValue = Math.floorMod(loginId.hashCode() + session * 31, 7);
        if (OFTEN_ABSENT.contains(loginId)) {
            return seedValue < 4 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
        }
        return seedValue == 0 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
    }

    private static final String[] WARM_UP_ANSWERS = {
        "Problems 1 to 5 are solved below with the loop invariants written out.",
        "Answers to all five problems. Problem 3 uses two pointers instead of a nested loop.",
        "1: 15, 2: index 4, 3: reversed in place, 4: O(n) with one pass, 5: the array grows by doubling.",
        "Solutions attached as text. I was unsure about problem 5's amortised cost.",
    };

    private static final String[] WARM_UP_FEEDBACK = {
        "Clear and correct. Well done.",
        "Problems 1 to 4 are right; problem 5 needs the amortised argument.",
        "Good working, but problem 3 should not use extra memory.",
        "Several off-by-one errors in the loops. Trace them with a small example.",
    };

    private static final String[] CLASSIFICATION_ANSWERS = {
        "All ten signals classified, with the even and odd parts worked out for signals 3 and 7.",
        "Signals 1 to 10 classified; I treated signal 9 as a power signal because it never decays.",
        "Working shown for each signal. Signal 6 is periodic with period 2π/3.",
    };

    private static final String[] CLASSIFICATION_FEEDBACK = {
        "Accurate throughout. Your even and odd decompositions are neat.",
        "Signal 9 is right, but signal 4 is an energy signal: its energy integral converges.",
        "Check the period of signal 6 again: the ratio of the two periods is irrational.",
    };
}
