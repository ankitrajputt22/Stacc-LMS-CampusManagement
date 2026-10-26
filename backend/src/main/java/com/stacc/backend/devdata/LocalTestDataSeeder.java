package com.stacc.backend.devdata;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.stacc.backend.academic.admin.AcademicCalendarManagementService;
import com.stacc.backend.academic.admin.AcademicCalendarRequests.AcademicCalendarEventCreate;
import com.stacc.backend.academic.attendance.AttendanceManagementService;
import com.stacc.backend.academic.attendance.AttendanceManagementService.RecordChange;
import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.result.CourseResultManagementService;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.approval.ApprovalApiRequests.ApprovalWorkflowStepRequest;
import com.stacc.backend.approval.ApprovalApiRequests.CreateApprovalWorkflowRequest;
import com.stacc.backend.approval.ApprovalCommands.StartApprovalCommand;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalWorkflowDefinitionRepository;
import com.stacc.backend.approval.ApprovalWorkflowDefinitionService;
import com.stacc.backend.approval.ApprovalWorkflowService;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.campus.notices.CampusNoticeInput;
import com.stacc.backend.campus.notices.CampusNoticeRepository;
import com.stacc.backend.campus.notices.CampusNoticeService;
import com.stacc.backend.campus.services.CampusServiceDefinitionRepository;
import com.stacc.backend.campus.services.CampusServiceDefinitionService;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceInput;
import com.stacc.backend.hostel.HostelBedRepository;
import com.stacc.backend.hostel.HostelManagementService;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelRequests.CreateBedRequest;
import com.stacc.backend.hostel.HostelRequests.CreateHostelRequest;
import com.stacc.backend.hostel.HostelRequests.CreateRoomRequest;
import com.stacc.backend.hostel.HostelRoomRepository;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.lms.announcement.LmsAnnouncement;
import com.stacc.backend.lms.announcement.LmsAnnouncementManagementService;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentManagementService;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentGradingService;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.content.LmsContentManagementService;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsModule;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.management.LmsCourseManagementService;
import com.stacc.backend.support.helpdesk.HelpdeskActor;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryRepository;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryService;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskTicketRequest;
import com.stacc.backend.support.helpdesk.HelpdeskTicketCommandService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketPriority;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills a local development database with connected demo data, so every finished feature can be tried by hand: test
 * accounts for each role and role combination, one department and program with two sessions, semesters, courses,
 * offerings, profiles, enrollments, and teaching, LMS courses in each state with content, announcements,
 * assignments, submissions, and grades, attendance, results, and calendar events. See {@code docs/local-test-data.md}.
 *
 * <p>It exists only under the {@value #PROFILE} profile, which is never on by default, and refuses to run next to a
 * production profile. Every account gets the one password in {@value #PASSWORD_VARIABLE}, hashed with the shared
 * {@link PasswordEncoder}; it is never logged. Each record is looked up by its natural key first and only created when
 * missing, so running it again adds nothing and changes nothing that exists, not even an existing account's password.
 * Records are created through the same services the API uses wherever one exists, so their rules apply: LMS
 * memberships come from provisioning, rosters are snapshotted, and attempt numbers are reserved as usual. Everything
 * happens in one transaction, so a failed run leaves nothing half done. Dates are relative to the day it runs.
 */
@Component
@Profile(LocalTestDataSeeder.PROFILE)
public class LocalTestDataSeeder implements ApplicationRunner {

    public static final String PROFILE = "dev-seed";
    static final String PASSWORD_VARIABLE = "STACC_TEST_PASSWORD";
    static final int MIN_PASSWORD_LENGTH = 8;
    /** The login IDs of the test accounts, in the order they are documented. */
    static final List<String> TEST_LOGIN_IDS = List.of(
            "student1", "student2", "faculty1", "faculty2", "admin1", "studentfaculty1", "facultyadmin1");

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTestDataSeeder.class);
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final Set<LmsContentStatus> ANY_CONTENT_STATUS = EnumSet.allOf(LmsContentStatus.class);
    static final String DEMO_APPROVAL_WORKFLOW = "DEMO_TWO_STEP_APPROVAL";
    /** The campus workflow V57 creates; the seeded services that need approval use it. */
    static final String CAMPUS_APPROVAL_WORKFLOW = "CAMPUS_SERVICE_REQUEST";
    static final String DEMO_APPROVAL_SOURCE = "DEV_DEMO";

    private final Environment environment;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Repositories repositories;
    private final Services services;

    private int created;
    private int existing;

    /** The repositories the seeder reads and writes, grouped to keep the constructor readable. */
    @Component
    @Profile(PROFILE)
    static class Repositories {
        final UserAccountRepository accounts;
        final RoleRepository roles;
        final DepartmentRepository departments;
        final ProgramRepository programs;
        final AcademicSessionRepository sessions;
        final SemesterRepository semesters;
        final CourseRepository courses;
        final CourseOfferingRepository offerings;
        final StudentProfileRepository studentProfiles;
        final FacultyProfileRepository facultyProfiles;
        final TeachingAssignmentRepository teaching;
        final SemesterEnrollmentRepository semesterEnrollments;
        final CourseEnrollmentRepository courseEnrollments;
        final LmsCourseRepository lmsCourses;
        final LmsModuleRepository modules;
        final LmsTopicRepository topics;
        final LmsAnnouncementRepository announcements;
        final LmsAssignmentRepository assignments;
        final LmsAssignmentSubmissionRepository submissions;
        final LmsSubmissionAttemptRepository attempts;
        final LmsSubmissionGradeRepository grades;
        final AttendanceSessionRepository attendanceSessions;
        final AttendanceRecordRepository attendanceRecords;
        final CourseResultRepository results;
        final AcademicCalendarEventRepository calendarEvents;
        final HelpdeskCategoryRepository helpdeskCategories;
        final HelpdeskTicketRepository helpdeskTickets;
        final ApprovalWorkflowDefinitionRepository approvalWorkflows;
        final HostelRepository hostels;
        final HostelRoomRepository hostelRooms;
        final HostelBedRepository hostelBeds;
        final CampusServiceDefinitionRepository campusServices;
        final CampusNoticeRepository campusNotices;

        Repositories(UserAccountRepository accounts, RoleRepository roles, DepartmentRepository departments,
                ProgramRepository programs, AcademicSessionRepository sessions, SemesterRepository semesters,
                CourseRepository courses, CourseOfferingRepository offerings, StudentProfileRepository studentProfiles,
                FacultyProfileRepository facultyProfiles, TeachingAssignmentRepository teaching,
                SemesterEnrollmentRepository semesterEnrollments, CourseEnrollmentRepository courseEnrollments,
                LmsCourseRepository lmsCourses, LmsModuleRepository modules, LmsTopicRepository topics,
                LmsAnnouncementRepository announcements, LmsAssignmentRepository assignments,
                LmsAssignmentSubmissionRepository submissions, LmsSubmissionAttemptRepository attempts,
                LmsSubmissionGradeRepository grades, AttendanceSessionRepository attendanceSessions,
                AttendanceRecordRepository attendanceRecords, CourseResultRepository results,
                AcademicCalendarEventRepository calendarEvents, HelpdeskCategoryRepository helpdeskCategories,
                HelpdeskTicketRepository helpdeskTickets, ApprovalWorkflowDefinitionRepository approvalWorkflows,
                HostelRepository hostels, HostelRoomRepository hostelRooms, HostelBedRepository hostelBeds,
                CampusServiceDefinitionRepository campusServices, CampusNoticeRepository campusNotices) {
            this.accounts = accounts;
            this.roles = roles;
            this.departments = departments;
            this.programs = programs;
            this.sessions = sessions;
            this.semesters = semesters;
            this.courses = courses;
            this.offerings = offerings;
            this.studentProfiles = studentProfiles;
            this.facultyProfiles = facultyProfiles;
            this.teaching = teaching;
            this.semesterEnrollments = semesterEnrollments;
            this.courseEnrollments = courseEnrollments;
            this.lmsCourses = lmsCourses;
            this.modules = modules;
            this.topics = topics;
            this.announcements = announcements;
            this.assignments = assignments;
            this.submissions = submissions;
            this.attempts = attempts;
            this.grades = grades;
            this.attendanceSessions = attendanceSessions;
            this.attendanceRecords = attendanceRecords;
            this.results = results;
            this.calendarEvents = calendarEvents;
            this.helpdeskCategories = helpdeskCategories;
            this.helpdeskTickets = helpdeskTickets;
            this.approvalWorkflows = approvalWorkflows;
            this.hostels = hostels;
            this.hostelRooms = hostelRooms;
            this.hostelBeds = hostelBeds;
            this.campusServices = campusServices;
            this.campusNotices = campusNotices;
        }
    }

    /** The feature services the seeder acts through, so their rules apply to the demo data too. */
    @Component
    @Profile(PROFILE)
    static class Services {
        final LmsCourseManagementService lmsCourses;
        final LmsContentManagementService content;
        final LmsAnnouncementManagementService announcements;
        final LmsAssignmentManagementService assignments;
        final LmsAssignmentSubmissionService submissions;
        final LmsAssignmentGradingService grading;
        final AttendanceManagementService attendance;
        final CourseResultManagementService results;
        final AcademicCalendarManagementService calendar;
        final HelpdeskCategoryService helpdeskCategories;
        final HelpdeskTicketCommandService helpdeskTickets;
        final ApprovalWorkflowDefinitionService approvalWorkflows;
        final ApprovalWorkflowService approvals;
        final ApprovalQueryService approvalQueries;
        final HostelManagementService hostels;
        final CampusServiceDefinitionService campusServices;
        final CampusNoticeService campusNotices;

        Services(LmsCourseManagementService lmsCourses, LmsContentManagementService content,
                LmsAnnouncementManagementService announcements, LmsAssignmentManagementService assignments,
                LmsAssignmentSubmissionService submissions, LmsAssignmentGradingService grading,
                AttendanceManagementService attendance, CourseResultManagementService results,
                AcademicCalendarManagementService calendar, HelpdeskCategoryService helpdeskCategories,
                HelpdeskTicketCommandService helpdeskTickets, ApprovalWorkflowDefinitionService approvalWorkflows,
                ApprovalWorkflowService approvals, ApprovalQueryService approvalQueries,
                HostelManagementService hostels, CampusServiceDefinitionService campusServices,
                CampusNoticeService campusNotices) {
            this.lmsCourses = lmsCourses;
            this.content = content;
            this.announcements = announcements;
            this.assignments = assignments;
            this.submissions = submissions;
            this.grading = grading;
            this.attendance = attendance;
            this.results = results;
            this.calendar = calendar;
            this.helpdeskCategories = helpdeskCategories;
            this.helpdeskTickets = helpdeskTickets;
            this.approvalWorkflows = approvalWorkflows;
            this.approvals = approvals;
            this.approvalQueries = approvalQueries;
            this.hostels = hostels;
            this.campusServices = campusServices;
            this.campusNotices = campusNotices;
        }
    }

    public LocalTestDataSeeder(Environment environment, TransactionTemplate transaction, PasswordEncoder passwordEncoder,
            Clock clock, Repositories repositories, Services services) {
        this.environment = environment;
        this.transaction = transaction;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.repositories = repositories;
        this.services = services;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String password = checkedPassword(environment);
        created = 0;
        existing = 0;
        transaction.executeWithoutResult(status -> seed(password));
        LOGGER.info("Local test data ({} profile): {} records created, {} already present. Test accounts: {}.",
                PROFILE, created, existing, String.join(", ", TEST_LOGIN_IDS));
    }

    /**
     * The test password, after checking that seeding is allowed here. A production profile next to this one, and a
     * missing or short password, stop the start-up with a message that never contains the password.
     */
    static String checkedPassword(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "The " + PROFILE + " profile creates test accounts and must never run with a production profile.");
            }
        }
        String password = environment.getProperty(PASSWORD_VARIABLE);
        if (password == null || password.isBlank() || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Set " + PASSWORD_VARIABLE + " to at least " + MIN_PASSWORD_LENGTH
                    + " characters to create the local test accounts of the " + PROFILE + " profile.");
        }
        return password;
    }

    private void seed(String password) {
        LocalDate today = LocalDate.now(clock);
        // The academic year runs from July to June. The current session is the one today falls in.
        int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;

        // --- Accounts: one password, roles assigned by the college, permissions from the roles.
        UserAccount student1 = account("student1", password, RoleName.STUDENT);
        UserAccount student2 = account("student2", password, RoleName.STUDENT);
        UserAccount faculty1 = account("faculty1", password, RoleName.FACULTY);
        UserAccount faculty2 = account("faculty2", password, RoleName.FACULTY);
        UserAccount admin1 = account("admin1", password, RoleName.ADMIN);
        UserAccount studentFaculty = account("studentfaculty1", password, RoleName.STUDENT, RoleName.FACULTY);
        UserAccount facultyAdmin = account("facultyadmin1", password, RoleName.FACULTY, RoleName.ADMIN);

        // --- Academic masters.
        Department cse = findOrCreate(repositories.departments.findByCode("CSE"),
                () -> repositories.departments.save(new Department("CSE", "Computer Science and Engineering")));
        Program btech = findOrCreate(repositories.programs.findByDepartmentIdAndCode(cse.getId(), "BTECH-CSE"),
                () -> repositories.programs.save(
                        new Program(cse, "BTECH-CSE", "Bachelor of Technology in Computer Science and Engineering", 8)));
        AcademicSession previous = session(startYear - 1, AcademicSession::close);
        AcademicSession current = session(startYear, AcademicSession::markActive);
        Semester previousSecond = semester(btech, previous, 2, Semester::close);
        Semester third = semester(btech, current, 3, Semester::markActive);
        Semester fourth = semester(btech, current, 4, semester -> { });

        Course oop = course(cse, "CS201", "Object-Oriented Programming", "4.00");
        Course dataStructures = course(cse, "CS301", "Data Structures", "4.00");
        Course databases = course(cse, "CS302", "Database Systems", "4.00");
        Course operatingSystems = course(cse, "CS303", "Operating Systems", "3.00");
        Course discreteMaths = course(cse, "CS304", "Discrete Mathematics", "3.00");
        Course networks = course(cse, "CS401", "Computer Networks", "4.00");

        CourseOffering cs201 = offering(oop, previousSecond, CourseOffering::close);
        CourseOffering cs301 = offering(dataStructures, third, CourseOffering::markActive);
        CourseOffering cs302 = offering(databases, third, CourseOffering::markActive);
        CourseOffering cs303 = offering(operatingSystems, third, CourseOffering::markActive);
        CourseOffering cs304 = offering(discreteMaths, third, CourseOffering::markActive);
        CourseOffering cs401 = offering(networks, fourth, offering -> { });

        // --- Profiles. Students were admitted in the previous session; no profile grants LMS access by itself.
        StudentProfile student1Profile = studentProfile(student1, btech, previous);
        StudentProfile student2Profile = studentProfile(student2, btech, previous);
        StudentProfile studentFacultyStudent = studentProfile(studentFaculty, btech, previous);
        FacultyProfile faculty1Profile = facultyProfile(faculty1, cse);
        FacultyProfile faculty2Profile = facultyProfile(faculty2, cse);
        FacultyProfile studentFacultyTeacher = facultyProfile(studentFaculty, cse);
        FacultyProfile facultyAdminProfile = facultyProfile(facultyAdmin, cse);

        // --- Teaching: several faculty may share an offering.
        teaching(faculty1Profile, cs201);
        teaching(faculty1Profile, cs301);
        teaching(faculty1Profile, cs303);
        teaching(faculty2Profile, cs302);
        teaching(faculty2Profile, cs304);
        teaching(studentFacultyTeacher, cs302);
        teaching(facultyAdminProfile, cs401);

        // --- Official enrollment, before any LMS course, so creating one provisions its current students.
        SemesterEnrollment student1Previous = semesterEnrollment(student1Profile, previousSecond, SemesterEnrollment::markCompleted);
        courseEnrollment(student1Previous, cs201, CourseEnrollment::markCompleted);
        SemesterEnrollment student1Third = semesterEnrollment(student1Profile, third, enrollment -> { });
        CourseEnrollment student1In301 = courseEnrollment(student1Third, cs301, enrollment -> { });
        courseEnrollment(student1Third, cs302, enrollment -> { });
        courseEnrollment(student1Third, cs303, enrollment -> { });
        courseEnrollment(student1Third, cs304, enrollment -> { });
        SemesterEnrollment student2Third = semesterEnrollment(student2Profile, third, enrollment -> { });
        CourseEnrollment student2In301 = courseEnrollment(student2Third, cs301, enrollment -> { });
        courseEnrollment(student2Third, cs302, enrollment -> { });
        courseEnrollment(student2Third, cs304, CourseEnrollment::withdraw);
        SemesterEnrollment studentFacultyThird = semesterEnrollment(studentFacultyStudent, third, enrollment -> { });
        CourseEnrollment studentFacultyIn301 = courseEnrollment(studentFacultyThird, cs301, enrollment -> { });

        // --- LMS courses: published, draft, archived, and CS304 and CS401 without one.
        Long lms301 = lmsCourse(faculty1, cs301, LmsCourseStatus.PUBLISHED);
        Long lms302 = lmsCourse(faculty2, cs302, LmsCourseStatus.DRAFT);
        lmsCourse(faculty1, cs303, LmsCourseStatus.ARCHIVED);

        Long unit1 = module(faculty1, lms301, "Unit 1: Arrays and Linked Lists",
                "Contiguous storage, then nodes and pointers.", true);
        topic(faculty1, unit1, "Arrays and memory layout",
                "How arrays are stored, indexed, and resized.\nRead chapter 2 before the lab.", true);
        topic(faculty1, unit1, "Singly linked lists", "Insertion, deletion, and traversal with a head pointer.", true);
        topic(faculty1, unit1, "Doubly linked lists", "Draft notes: previous pointers and sentinel nodes.", false);
        Long unit2 = module(faculty1, lms301, "Unit 2: Stacks and Queues", "Being prepared.", false);
        topic(faculty1, unit2, "Stack operations", "Push, pop, and peek with arrays and lists.", false);
        Long relational = module(faculty2, lms302, "Unit 1: The Relational Model", "Tables, keys, and constraints.", false);
        topic(faculty2, relational, "Keys and constraints", "Primary keys, foreign keys, and uniqueness.", false);

        announcement(faculty1, lms301, "Welcome to Data Structures",
                "Labs start this week. Bring your laptop and read the course outline.", true);
        announcement(faculty1, lms301, "Lab room change", "Draft: the Thursday lab may move to Lab 3.", false);
        announcement(faculty2, lms302, "Course outline coming soon", "Draft: the outline will be shared before classes.", false);

        // --- Assignments with past, future, draft, and archived deadlines.
        LmsAssignment warmUp = assignment(faculty1, lms301, "Array warm-up exercises",
                "Solve problems 1 to 5 from the arrays worksheet.", deadline(today, -5), "20", LmsContentStatus.PUBLISHED);
        LmsAssignment linkedList = assignment(faculty1, lms301, "Linked list implementation",
                "Implement a singly linked list with insert, delete, and search.", deadline(today, 9), "50",
                LmsContentStatus.PUBLISHED);
        assignment(faculty1, lms301, "Stack and queue problems", "Draft: problems on stacks and queues.",
                deadline(today, 16), "20", LmsContentStatus.DRAFT);
        assignment(faculty1, lms301, "Old practice quiz", "A practice quiz kept for history.", deadline(today, -20), "10",
                LmsContentStatus.ARCHIVED);
        assignment(faculty2, lms302, "ER diagram exercise", "Draw the ER diagram for the library case study.",
                deadline(today, 12), "25", LmsContentStatus.DRAFT);

        // --- Submissions and grades: a graded late attempt, ungraded latest attempts, and a graded earlier attempt.
        submit(student1, lms301, warmUp, student1In301, "Answers to problems 1 to 5 are below.");
        grade(faculty1, warmUp, student1In301, 1, "17", "Good work. Problem 4 needs a tighter loop bound.");
        submit(student1, lms301, linkedList, student1In301, "My linked list with insert, delete, and search.");
        submit(student2, lms301, linkedList, student2In301, "First version: insert and search work.",
                "Second version: delete now handles the head node.");
        grade(faculty1, linkedList, student2In301, 1, "38", "Insert and search are correct; delete is missing.");
        submit(studentFaculty, lms301, warmUp, studentFacultyIn301, "Problems 1 to 3 are done.");

        // --- Attendance: two finalized sessions and an open one in CS301, an open unmarked one in CS302.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        attendance(faculty1, cs301, "Arrays recap", now.minus(6, ChronoUnit.DAYS), Map.of(
                "student1", AttendanceStatus.PRESENT, "student2", AttendanceStatus.ABSENT,
                "studentfaculty1", AttendanceStatus.PRESENT), true);
        attendance(faculty1, cs301, "Linked lists lab", now.minus(2, ChronoUnit.DAYS), Map.of(
                "student1", AttendanceStatus.PRESENT, "student2", AttendanceStatus.PRESENT,
                "studentfaculty1", AttendanceStatus.ABSENT), true);
        attendance(faculty1, cs301, "Stacks introduction", now.minus(1, ChronoUnit.HOURS),
                Map.of("student1", AttendanceStatus.PRESENT), false);
        attendance(faculty2, cs302, "Relational model basics", now.minus(1, ChronoUnit.DAYS), Map.of(), false);

        // --- Official results: published and draft, and a published one of the previous session.
        result(faculty1, cs301, student1In301, "86", "100", "A", null, true);
        result(faculty1, cs301, student2In301, "71", "100", null, "Draft until the moderation meeting.", false);
        CourseEnrollment student1In201 = repositories.courseEnrollments
                .findBySemesterEnrollmentIdAndCourseOfferingId(student1Previous.getId(), cs201.getId()).orElseThrow();
        result(faculty1, cs201, student1In201, "78", "100", "B+", "Consistent work across the semester.", true);

        // --- Academic calendar: published, draft, and cancelled after publishing, around today.
        calendarEvent(current, null, "Next semester course registration", AcademicCalendarEventCategory.REGISTRATION,
                today.minusDays(2), today.plusDays(5), "Register for next semester's courses with your department.",
                AcademicCalendarEventStatus.PUBLISHED);
        calendarEvent(current, third, "Department seminar", AcademicCalendarEventCategory.ACADEMIC, today.plusDays(4),
                today.plusDays(4), "Called off; a new date will be announced.", AcademicCalendarEventStatus.CANCELLED);
        calendarEvent(current, null, "Faculty planning day", AcademicCalendarEventCategory.OTHER, today.plusDays(7),
                today.plusDays(7), null, AcademicCalendarEventStatus.DRAFT);
        calendarEvent(current, third, "Mid-semester examinations", AcademicCalendarEventCategory.EXAM, today.plusDays(10),
                today.plusDays(15), "Examinations follow the published timetable.", AcademicCalendarEventStatus.PUBLISHED);
        calendarEvent(current, null, "Autumn break", AcademicCalendarEventCategory.HOLIDAY, today.plusDays(21),
                today.plusDays(25), null, AcademicCalendarEventStatus.PUBLISHED);
        calendarEvent(current, fourth, "Semester 4 orientation", AcademicCalendarEventCategory.ACADEMIC,
                today.plusDays(30), today.plusDays(30), null, AcademicCalendarEventStatus.PUBLISHED);

        // --- Helpdesk: the four categories, and tickets in every status. Changes go through the ticket engine, so
        // history and Inbox notifications are written as usual.
        Long general = helpdeskCategory("GENERAL", "General", "Questions that fit no other category.");
        Long academic = helpdeskCategory("ACADEMIC", "Academic records",
                "Enrollment, attendance, results, and the academic calendar.");
        Long lms = helpdeskCategory("LMS", "LMS and courses", "Course access, content, assignments, and submissions.");
        Long technical = helpdeskCategory("TECHNICAL", "Technical issues",
                "Sign-in, devices, classrooms, and the campus network.");
        ticket(student1, lms, "CS302 is missing from my courses",
                "CS302 is on my enrollment but it does not appear under Courses.", HelpdeskTicketPriority.NORMAL,
                List.of(), null, null, HelpdeskTicketStatus.OPEN);
        ticket(faculty1, technical, "Projector in room 204 does not turn on",
                "The projector shows no picture since Monday. I teach CS301 there every morning.",
                HelpdeskTicketPriority.HIGH, List.of("A technician will check the projector tomorrow morning."),
                admin1, facultyAdmin, HelpdeskTicketStatus.IN_PROGRESS);
        ticket(student1, general, "Bonafide certificate for a scholarship",
                "I need a bonafide certificate for a scholarship application.", HelpdeskTicketPriority.LOW,
                List.of("Your certificate is ready at the front office."), admin1, admin1,
                HelpdeskTicketStatus.RESOLVED);
        ticket(student2, academic, "Attendance shows me absent for the arrays recap",
                "I was in the arrays recap session of CS301, but my attendance shows absent.",
                HelpdeskTicketPriority.NORMAL,
                List.of("Your teacher confirmed the record; it was corrected in the attendance register."), admin1,
                admin1, HelpdeskTicketStatus.CLOSED);

        // --- Approvals: one clearly labelled demo workflow for trying the engine, and demo requests waiting at its
        // first step. There is no approval page yet; real workflows come with the modules that use them.
        approvalWorkflow(DEMO_APPROVAL_WORKFLOW, "Demo two-step approval (development only)",
                "For trying the approval engine on local test data. Not a real college process.");
        demoApproval(student1, "demo-1", "Demo approval request 1 (development only)");
        demoApproval(student2, "demo-2", "Demo approval request 2 (development only)");
        demoApproval(studentFaculty, "demo-3", "Demo approval request 3 (development only)");

        // --- Hostel: a small inventory to try applications and allocations. Real hostels are added by admins.
        Long mainHostel = hostel("MAIN", "Main Hostel", "Development test hostel.");
        for (String roomNumber : List.of("A-101", "A-102")) {
            Long roomId = hostelRoom(mainHostel, roomNumber);
            hostelBed(roomId, "B1");
            hostelBed(roomId, "B2");
        }

        // --- Campus: a small service catalog and notices to try requests and the notice feed. Development data only;
        // real services and notices are added by admins.
        campusService("ID_CARD", "ID card replacement", "Replace a lost or damaged college ID card.", true, true,
                CAMPUS_APPROVAL_WORKFLOW);
        campusService("TRANSPORT", "Transport pass", "Apply for a college bus pass.", true, false,
                CAMPUS_APPROVAL_WORKFLOW);
        campusService("GENERAL_ADMIN", "General administrative request",
                "Letters and other routine requests to the administrative office.", true, true, null);
        campusNotice("Welcome to Campus services (development)",
                "Request campus services and read college notices on the Campus page. This is development test data.",
                true, true, true, true);
        campusNotice("Library hours during exams (development draft)",
                "The library stays open until 10 pm during the exam weeks. This draft is development test data.",
                true, true, false, false);
    }

    // --- Find or create. Each helper creates only what is missing and never changes what exists.

    private <T> T findOrCreate(Optional<T> found, Supplier<T> create) {
        if (found.isPresent()) {
            existing++;
            return found.get();
        }
        created++;
        return create.get();
    }

    private UserAccount account(String loginId, String password, RoleName... roleNames) {
        UserAccount account = findOrCreate(repositories.accounts.findByLoginId(loginId),
                () -> new UserAccount(loginId, passwordEncoder.encode(password)));
        // Missing roles are added; roles are never removed and Role rows are never created here.
        for (RoleName name : roleNames) {
            if (!account.hasRole(name)) {
                account.assignRole(repositories.roles.findByName(name)
                        .orElseThrow(() -> new IllegalStateException("Role " + name + " is missing")));
            }
        }
        return repositories.accounts.save(account);
    }

    private AcademicSession session(int startYear, Consumer<AcademicSession> lifecycle) {
        String code = startYear + "-" + String.format("%02d", (startYear + 1) % 100);
        return findOrCreate(repositories.sessions.findByCode(code), () -> {
            AcademicSession session = new AcademicSession(code, LocalDate.of(startYear, 7, 1),
                    LocalDate.of(startYear + 1, 6, 30));
            lifecycle.accept(session);
            return repositories.sessions.save(session);
        });
    }

    private Semester semester(Program program, AcademicSession session, int number, Consumer<Semester> lifecycle) {
        return findOrCreate(
                repositories.semesters.findByProgramIdAndAcademicSessionIdAndSemesterNumber(program.getId(), session.getId(), number),
                () -> {
                    Semester semester = new Semester(program, session, number);
                    lifecycle.accept(semester);
                    return repositories.semesters.save(semester);
                });
    }

    private Course course(Department department, String code, String name, String credits) {
        return findOrCreate(repositories.courses.findByCode(code),
                () -> repositories.courses.save(new Course(department, code, name, new BigDecimal(credits))));
    }

    private CourseOffering offering(Course course, Semester semester, Consumer<CourseOffering> lifecycle) {
        return findOrCreate(repositories.offerings.findByCourseIdAndSemesterId(course.getId(), semester.getId()), () -> {
            CourseOffering offering = new CourseOffering(course, semester);
            lifecycle.accept(offering);
            return repositories.offerings.save(offering);
        });
    }

    private StudentProfile studentProfile(UserAccount account, Program program, AcademicSession admission) {
        return findOrCreate(repositories.studentProfiles.findByUserAccountId(account.getId()),
                () -> repositories.studentProfiles.save(new StudentProfile(account, program, admission)));
    }

    private FacultyProfile facultyProfile(UserAccount account, Department department) {
        return findOrCreate(repositories.facultyProfiles.findByUserAccountId(account.getId()),
                () -> repositories.facultyProfiles.save(new FacultyProfile(account, department)));
    }

    private void teaching(FacultyProfile faculty, CourseOffering offering) {
        findOrCreate(repositories.teaching.findByFacultyProfileIdAndCourseOfferingId(faculty.getId(), offering.getId()),
                () -> repositories.teaching.save(new TeachingAssignment(faculty, offering)));
    }

    private SemesterEnrollment semesterEnrollment(StudentProfile student, Semester semester,
            Consumer<SemesterEnrollment> lifecycle) {
        return findOrCreate(repositories.semesterEnrollments.findByStudentProfileIdAndSemesterId(student.getId(), semester.getId()),
                () -> {
                    SemesterEnrollment enrollment = repositories.semesterEnrollments.save(new SemesterEnrollment(student, semester));
                    lifecycle.accept(enrollment);
                    return enrollment;
                });
    }

    private CourseEnrollment courseEnrollment(SemesterEnrollment semesterEnrollment, CourseOffering offering,
            Consumer<CourseEnrollment> lifecycle) {
        return findOrCreate(repositories.courseEnrollments.findBySemesterEnrollmentIdAndCourseOfferingId(
                        semesterEnrollment.getId(), offering.getId()),
                () -> {
                    CourseEnrollment enrollment = repositories.courseEnrollments.saveAndFlush(
                            new CourseEnrollment(semesterEnrollment, offering));
                    lifecycle.accept(enrollment);
                    return repositories.courseEnrollments.saveAndFlush(enrollment);
                });
    }

    /** Creates the LMS course as its teacher would, which provisions its current students, then moves it on. */
    private Long lmsCourse(UserAccount faculty, CourseOffering offering, LmsCourseStatus target) {
        Optional<LmsCourse> found = repositories.lmsCourses.findByCourseOfferingId(offering.getId());
        if (found.isPresent()) {
            existing++;
            return found.get().getId();
        }
        created++;
        Long lmsCourseId = services.lmsCourses.create(faculty.getId(), offering.getId()).lmsCourseId();
        if (target != LmsCourseStatus.DRAFT) {
            services.lmsCourses.publish(faculty.getId(), lmsCourseId);
        }
        if (target == LmsCourseStatus.ARCHIVED) {
            services.lmsCourses.archive(faculty.getId(), lmsCourseId);
        }
        return lmsCourseId;
    }

    private Long module(UserAccount faculty, Long lmsCourseId, String title, String description, boolean publish) {
        Optional<LmsModule> found = repositories.modules
                .findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(lmsCourseId, ANY_CONTENT_STATUS).stream()
                .filter(module -> module.getTitle().equals(title))
                .findFirst();
        return findOrCreate(found, () -> {
            LmsModule module = services.content.createModule(faculty.getId(), lmsCourseId, title, description);
            if (publish) {
                services.content.publishModule(faculty.getId(), module.getId());
            }
            return module;
        }).getId();
    }

    private void topic(UserAccount faculty, Long moduleId, String title, String content, boolean publish) {
        boolean exists = repositories.topics
                .findAllByLmsModuleIdAndStatusInOrderByPositionAscIdAsc(moduleId, ANY_CONTENT_STATUS).stream()
                .anyMatch(topic -> topic.getTitle().equals(title));
        createUnless(exists, () -> {
            Long topicId = services.content.createTopic(faculty.getId(), moduleId, title, content).getId();
            if (publish) {
                services.content.publishTopic(faculty.getId(), topicId);
            }
        });
    }

    private void announcement(UserAccount faculty, Long lmsCourseId, String title, String message, boolean publish) {
        boolean exists = repositories.announcements
                .findAllByLmsCourseIdAndStatusInOrderByCreatedAtDescIdDesc(lmsCourseId, ANY_CONTENT_STATUS).stream()
                .map(LmsAnnouncement::getTitle)
                .anyMatch(title::equals);
        createUnless(exists, () -> {
            Long announcementId = services.announcements.create(faculty.getId(), lmsCourseId, title, message).getId();
            if (publish) {
                services.announcements.publish(faculty.getId(), announcementId);
            }
        });
    }

    private LmsAssignment assignment(UserAccount faculty, Long lmsCourseId, String title, String instructions,
            Instant dueAt, String maxMarks, LmsContentStatus target) {
        Optional<LmsAssignment> found = repositories.assignments
                .findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(lmsCourseId, ANY_CONTENT_STATUS).stream()
                .filter(assignment -> assignment.getTitle().equals(title))
                .findFirst();
        return findOrCreate(found, () -> {
            LmsAssignment assignment = services.assignments.create(faculty.getId(), lmsCourseId, title, instructions, dueAt,
                    new BigDecimal(maxMarks));
            if (target != LmsContentStatus.DRAFT) {
                services.assignments.publish(faculty.getId(), assignment.getId());
            }
            if (target == LmsContentStatus.ARCHIVED) {
                services.assignments.archive(faculty.getId(), assignment.getId());
            }
            return assignment;
        });
    }

    /** Hands in one text attempt per text, as the student would, unless the student has already submitted. */
    private void submit(UserAccount student, Long lmsCourseId, LmsAssignment assignment, CourseEnrollment enrollment,
            String... attemptTexts) {
        boolean exists = repositories.submissions
                .findByAssignmentIdAndCourseEnrollmentId(assignment.getId(), enrollment.getId()).isPresent();
        createUnless(exists, () -> {
            for (String text : attemptTexts) {
                services.submissions.submit(student.getId(), lmsCourseId, assignment.getId(), text, List.of());
            }
        });
    }

    private void grade(UserAccount faculty, LmsAssignment assignment, CourseEnrollment enrollment, int attemptNumber,
            String marks, String feedback) {
        LmsAssignmentSubmission submission = repositories.submissions
                .findByAssignmentIdAndCourseEnrollmentId(assignment.getId(), enrollment.getId()).orElseThrow();
        LmsSubmissionAttempt attempt = repositories.attempts.findAllBySubmissionIdOrderByAttemptNumberDesc(submission.getId())
                .stream()
                .filter(candidate -> candidate.getAttemptNumber() == attemptNumber)
                .findFirst()
                .orElseThrow();
        createUnless(repositories.grades.findByAttemptId(attempt.getId()).isPresent(),
                () -> services.grading.grade(faculty.getId(), attempt.getId(), new BigDecimal(marks), feedback));
    }

    /** Takes a session as its teacher would: the roster is the snapshot of current students, then marks and finalizes. */
    private void attendance(UserAccount faculty, CourseOffering offering, String topic, Instant heldAt,
            Map<String, AttendanceStatus> marks, boolean finalize) {
        boolean exists = repositories.attendanceSessions.findAllByCourseOfferingIdOrderByHeldAtDescIdDesc(offering.getId())
                .stream()
                .anyMatch(session -> topic.equals(session.getTopic()));
        createUnless(exists, () -> {
            Long sessionId = services.attendance.createSession(faculty.getId(), offering.getId(), heldAt, topic).getId();
            List<RecordChange> changes = repositories.attendanceRecords.findAllWithStudentBySessionId(sessionId).stream()
                    .filter(record -> marks.containsKey(loginIdOf(record)))
                    .map(record -> new RecordChange(record.getId(), marks.get(loginIdOf(record))))
                    .toList();
            if (!changes.isEmpty()) {
                services.attendance.saveRecords(faculty.getId(), sessionId, changes);
            }
            if (finalize) {
                services.attendance.finalizeSession(faculty.getId(), sessionId);
            }
        });
    }

    private void result(UserAccount faculty, CourseOffering offering, CourseEnrollment enrollment, String marks,
            String maxMarks, String gradeCode, String remarks, boolean publish) {
        createUnless(repositories.results.findByCourseEnrollmentId(enrollment.getId()).isPresent(), () -> {
            services.results.save(faculty.getId(), offering.getId(), enrollment.getId(), new BigDecimal(marks),
                    new BigDecimal(maxMarks), gradeCode, remarks);
            if (publish) {
                services.results.publish(faculty.getId(), offering.getId(), enrollment.getId());
            }
        });
    }

    private void calendarEvent(AcademicSession session, Semester semester, String title,
            AcademicCalendarEventCategory category, LocalDate startDate, LocalDate endDate, String description,
            AcademicCalendarEventStatus target) {
        boolean exists = repositories.calendarEvents.findAllWithContext(session.getId(), null, null, null, null).stream()
                .anyMatch(event -> event.getTitle().equals(title));
        createUnless(exists, () -> {
            Long eventId = services.calendar.create(new AcademicCalendarEventCreate(session.getId(),
                    semester == null ? null : semester.getId(), title, description, category, startDate, endDate)).eventId();
            if (target != AcademicCalendarEventStatus.DRAFT) {
                services.calendar.publish(eventId);
            }
            if (target == AcademicCalendarEventStatus.CANCELLED) {
                services.calendar.cancel(eventId);
            }
        });
    }

    private Long helpdeskCategory(String code, String name, String description) {
        return findOrCreate(repositories.helpdeskCategories.findByCode(code).map(category -> category.getId()),
                () -> services.helpdeskCategories.create(new CreateHelpdeskCategoryRequest(code, name, description, true))
                        .categoryId());
    }

    /**
     * Raises a ticket as its requester, then lets the support team assign it, answer it, and move it to its status,
     * all through the ticket engine. A ticket is known by its requester and subject.
     */
    private void ticket(UserAccount requester, Long categoryId, String subject, String description,
            HelpdeskTicketPriority priority, List<String> adminReplies, UserAccount assignee, UserAccount assignedBy,
            HelpdeskTicketStatus target) {
        boolean exists = repositories.helpdeskTickets.existsByRequesterIdAndSubject(requester.getId(), subject);
        createUnless(exists, () -> {
            HelpdeskActor asRequester = new HelpdeskActor(requester.getId(), false, true, true, false, false, false);
            Long ticketId = services.helpdeskTickets.create(asRequester,
                    new CreateHelpdeskTicketRequest(categoryId, subject, description, priority)).ticketId();
            if (assignee != null) {
                services.helpdeskTickets.assign(supportTeam(assignedBy), ticketId, assignee.getLoginId());
            }
            HelpdeskActor support = supportTeam(assignee);
            for (String reply : adminReplies) {
                services.helpdeskTickets.addMessage(support, ticketId, reply);
            }
            // A closed ticket was resolved first, as the support team usually does.
            if (target == HelpdeskTicketStatus.CLOSED) {
                services.helpdeskTickets.changeStatus(support, ticketId, HelpdeskTicketStatus.RESOLVED);
            }
            if (target != HelpdeskTicketStatus.OPEN) {
                services.helpdeskTickets.changeStatus(support, ticketId, target);
            }
        });
    }

    private void approvalWorkflow(String code, String name, String description) {
        boolean exists = repositories.approvalWorkflows.findWithStepsByCode(code).isPresent();
        createUnless(exists, () -> services.approvalWorkflows.create(new CreateApprovalWorkflowRequest(code, name,
                description, List.of(new ApprovalWorkflowStepRequest("Faculty review", "FACULTY"),
                        new ApprovalWorkflowStepRequest("Administrative approval", "ADMIN")))));
    }

    /** Starts a demo request through the engine, once per demo source ID. */
    private void demoApproval(UserAccount requester, String sourceId, String subject) {
        boolean exists = !services.approvalQueries.requestsForSource(DEMO_APPROVAL_SOURCE, sourceId).isEmpty();
        createUnless(exists, () -> services.approvals.startApproval(new StartApprovalCommand(DEMO_APPROVAL_WORKFLOW,
                requester.getId(), DEMO_APPROVAL_SOURCE, sourceId, subject)));
    }

    private Long hostel(String code, String name, String description) {
        return findOrCreate(repositories.hostels.findByCode(code).map(found -> found.getId()),
                () -> services.hostels.createHostel(new CreateHostelRequest(code, name, description)).hostelId());
    }

    private Long hostelRoom(Long hostelId, String roomNumber) {
        return findOrCreate(repositories.hostelRooms.findByHostelIdAndRoomNumber(hostelId, roomNumber).map(found -> found.getId()),
                () -> services.hostels.createRoom(hostelId, new CreateRoomRequest(roomNumber)).roomId());
    }

    private void hostelBed(Long roomId, String bedNumber) {
        createUnless(repositories.hostelBeds.existsByRoomIdAndBedNumber(roomId, bedNumber),
                () -> services.hostels.createBed(roomId, new CreateBedRequest(bedNumber)));
    }

    private void campusService(String code, String name, String description, boolean student, boolean faculty,
            String workflowCode) {
        createUnless(repositories.campusServices.existsByCode(code),
                () -> services.campusServices.create(new CreateCampusServiceInput(code, name, description, true, student,
                        faculty, workflowCode != null, workflowCode)));
    }

    /** A notice found by its title; a published one notifies its readers once, as an admin's would. */
    private void campusNotice(String title, String message, boolean student, boolean faculty, boolean admin,
            boolean publish) {
        createUnless(repositories.campusNotices.existsByTitle(title), () -> {
            Long noticeId = services.campusNotices.create(new CampusNoticeInput(title, message, student, faculty, admin))
                    .noticeId();
            if (publish) {
                services.campusNotices.publish(noticeId);
            }
        });
    }

    /** An admin acting with the helpdesk permissions every ADMIN account receives. */
    private static HelpdeskActor supportTeam(UserAccount admin) {
        return new HelpdeskActor(admin == null ? null : admin.getId(), true, true, true, true, true, true);
    }

    private void createUnless(boolean exists, Runnable create) {
        if (exists) {
            existing++;
        } else {
            created++;
            create.run();
        }
    }

    private static String loginIdOf(AttendanceRecord record) {
        return record.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId();
    }

    /** A deadline at 23:00 India time, some days from today. */
    private static Instant deadline(LocalDate today, int days) {
        return today.plusDays(days).atTime(17, 30).toInstant(ZoneOffset.UTC);
    }
}
