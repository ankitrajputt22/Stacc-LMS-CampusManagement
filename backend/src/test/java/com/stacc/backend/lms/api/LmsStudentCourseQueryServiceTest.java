package com.stacc.backend.lms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import com.stacc.backend.lms.membership.LmsStudentMembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Every test starts from a student with one course they may use, and then changes one thing.
 * Each test also asks the single-course access check about the same stored data, because the
 * list must never show a course that check would refuse.
 */
class LmsStudentCourseQueryServiceTest {

    // Made-up IDs. The LMS course ID is deliberately different from the course offering ID.
    private static final Long ACCOUNT_ID = 12L;
    private static final Long OTHER_ACCOUNT_ID = 13L;
    private static final Long LMS_COURSE_ID = 7L;
    private static final Long OFFERING_ID = 50L;

    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final LmsStudentMembershipRepository memberships = mock(LmsStudentMembershipRepository.class);
    private final LmsStudentCourseQueryService service = new LmsStudentCourseQueryService(memberships);
    private final LmsStudentAccessService accessCheck =
            new LmsStudentAccessService(studentProfiles, lmsCourses, memberships);

    private final Department department = new Department("TST", "Test Department");
    private final Program program = new Program(department, "BTECH", "Test Program", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);

    private final StudentProfile student = student("TEST-STUDENT-1", ACCOUNT_ID);
    private final SemesterEnrollment semesterEnrollment = new SemesterEnrollment(student, semester);
    private final CourseOffering offering = offering("TST301", "Test Course One", OFFERING_ID, semester);
    private final CourseEnrollment courseEnrollment = new CourseEnrollment(semesterEnrollment, offering);
    private final LmsCourse lmsCourse = publishedLmsCourse(offering, LMS_COURSE_ID);
    private final LmsStudentMembership membership = new LmsStudentMembership(courseEnrollment, lmsCourse);

    // What the stand-in repository holds for the student's account.
    private final List<LmsStudentMembership> stored = new ArrayList<>(List.of(membership));

    private StudentProfile student(String loginId, Long accountId) {
        UserAccount account = new UserAccount(loginId, "example-hash-value");
        ReflectionTestUtils.setField(account, "id", accountId);
        return new StudentProfile(account, program, session);
    }

    private CourseOffering offering(String courseCode, String courseName, Long id, Semester inSemester) {
        Course course = new Course(department, courseCode, courseName, new BigDecimal("4.00"));
        CourseOffering created = new CourseOffering(course, inSemester);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private LmsCourse lmsCourseStandIn(CourseOffering courseOffering, Long id) {
        LmsCourse created = new LmsCourse(courseOffering);
        ReflectionTestUtils.setField(created, "id", id);
        when(lmsCourses.findById(id)).thenReturn(Optional.of(created));
        return created;
    }

    private LmsCourse publishedLmsCourse(CourseOffering courseOffering, Long id) {
        LmsCourse created = lmsCourseStandIn(courseOffering, id);
        created.publish();
        return created;
    }

    /** One more course the student may use, with its own offering, enrollment, and membership. */
    private void anotherUsableCourse(String courseCode, Long offeringId, Long lmsCourseId, Semester inSemester) {
        CourseOffering another = offering(courseCode, "Test Course " + courseCode, offeringId, inSemester);
        stored.add(new LmsStudentMembership(
                new CourseEnrollment(semesterEnrollment, another), publishedLmsCourse(another, lmsCourseId)));
    }

    @BeforeEach
    void standInRepositories() {
        when(studentProfiles.findByUserAccountId(ACCOUNT_ID)).thenReturn(Optional.of(student));
        when(memberships.findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID)).thenAnswer(call -> List.copyOf(stored));
        when(memberships.findAllByUserAccountIdAndLmsCourseId(eq(ACCOUNT_ID), anyLong())).thenAnswer(call -> stored.stream()
                .filter(found -> call.getArgument(1).equals(found.getLmsCourse().getId()))
                .toList());
    }

    private List<Long> listedCourseIds() {
        return service.getAccessibleCourses(ACCOUNT_ID).stream()
                .map(LmsCourseSummaryResponse::lmsCourseId)
                .toList();
    }

    /** The list and the single-course access check must give the same answer for the same data. */
    private void expectCourseListed(boolean expected) {
        assertEquals(expected, listedCourseIds().contains(LMS_COURSE_ID), "listed in my courses");
        assertEquals(expected, accessCheck.canAccessCourse(ACCOUNT_ID, LMS_COURSE_ID), "single-course access check");
    }

    @Test
    void courseTheStudentMayUseIsListedWithItsDetails() {
        expectCourseListed(true);

        assertEquals(
                List.of(new LmsCourseSummaryResponse(
                        LMS_COURSE_ID, "TST301", "Test Course One", new BigDecimal("4.00"), 3, "BTECH", "2026-27")),
                service.getAccessibleCourses(ACCOUNT_ID));
    }

    @Test
    void inactiveStudentProfileListsNothing() {
        student.deactivate();

        expectCourseListed(false);
        assertTrue(service.getAccessibleCourses(ACCOUNT_ID).isEmpty());
    }

    @Test
    void completedSemesterEnrollmentIsLeftOut() {
        semesterEnrollment.markCompleted();

        expectCourseListed(false);
    }

    @Test
    void withdrawnSemesterEnrollmentIsLeftOut() {
        semesterEnrollment.withdraw();

        expectCourseListed(false);
    }

    @Test
    void completedCourseEnrollmentIsLeftOut() {
        courseEnrollment.markCompleted();

        expectCourseListed(false);
    }

    @Test
    void withdrawnCourseEnrollmentIsLeftOut() {
        courseEnrollment.withdraw();

        expectCourseListed(false);
    }

    @Test
    void inactiveMembershipIsLeftOut() {
        membership.deactivate();

        expectCourseListed(false);
    }

    @Test
    void draftLmsCourseIsLeftOut() {
        stored.clear();
        stored.add(new LmsStudentMembership(courseEnrollment, lmsCourseStandIn(offering, LMS_COURSE_ID)));

        expectCourseListed(false);
    }

    @Test
    void archivedLmsCourseIsLeftOut() {
        lmsCourse.archive();

        expectCourseListed(false);
    }

    @Test
    void courseEnrollmentForAnotherOfferingIsLeftOut() {
        CourseEnrollment elsewhere = new CourseEnrollment(
                semesterEnrollment, offering("TST302", "Test Course Two", 72L, semester));
        stored.clear();
        stored.add(new LmsStudentMembership(elsewhere, lmsCourse));

        expectCourseListed(false);
    }

    @Test
    void membershipOfAnotherStudentIsLeftOut() {
        StudentProfile classmate = student("TEST-STUDENT-2", OTHER_ACCOUNT_ID);
        CourseEnrollment theirs = new CourseEnrollment(new SemesterEnrollment(classmate, semester), offering);
        stored.clear();
        stored.add(new LmsStudentMembership(theirs, lmsCourse));

        expectCourseListed(false);
    }

    @Test
    void enrolledCourseWithoutAMembershipIsLeftOutAndNoMembershipIsCreated() {
        stored.clear();

        expectCourseListed(false);
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        verify(memberships, atLeastOnce()).findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID);
        verify(memberships, atLeastOnce()).findAllByUserAccountIdAndLmsCourseId(ACCOUNT_ID, LMS_COURSE_ID);
        verifyNoMoreInteractions(memberships);
    }

    @Test
    void onlyTheCoursesThatPassEveryRuleAreListed() {
        anotherUsableCourse("TST302", 51L, 8L, semester);
        anotherUsableCourse("TST303", 52L, 9L, semester);
        CourseOffering ended = offering("TST304", "Test Course Four", 53L, semester);
        CourseEnrollment endedEnrollment = new CourseEnrollment(semesterEnrollment, ended);
        endedEnrollment.withdraw();
        stored.add(new LmsStudentMembership(endedEnrollment, publishedLmsCourse(ended, 10L)));
        CourseOffering unpublished = offering("TST305", "Test Course Five", 54L, semester);
        stored.add(new LmsStudentMembership(
                new CourseEnrollment(semesterEnrollment, unpublished), lmsCourseStandIn(unpublished, 11L)));

        assertEquals(List.of(7L, 8L, 9L), listedCourseIds());
        for (long lmsCourseId = 7; lmsCourseId <= 11; lmsCourseId++) {
            assertEquals(
                    listedCourseIds().contains(lmsCourseId),
                    accessCheck.canAccessCourse(ACCOUNT_ID, lmsCourseId),
                    "LMS course " + lmsCourseId);
        }
    }

    @Test
    void coursesComeInCourseCodeOrderWhateverOrderTheyAreStoredIn() {
        stored.clear();
        anotherUsableCourse("TST303", 52L, 9L, semester);
        anotherUsableCourse("TST301", 50L, 7L, semester);
        anotherUsableCourse("TST302", 51L, 8L, semester);

        List<String> codes = service.getAccessibleCourses(ACCOUNT_ID).stream()
                .map(LmsCourseSummaryResponse::courseCode)
                .toList();

        assertEquals(List.of("TST301", "TST302", "TST303"), codes);
    }

    @Test
    void sameCourseInTwoSemestersIsOrderedByLmsCourseId() {
        Semester nextSemester = new Semester(program, session, 4);
        stored.clear();
        anotherUsableCourse("TST301", 60L, 21L, nextSemester);
        anotherUsableCourse("TST301", 50L, 20L, semester);

        List<LmsCourseSummaryResponse> courses = service.getAccessibleCourses(ACCOUNT_ID);

        assertEquals(List.of(20L, 21L), courses.stream().map(LmsCourseSummaryResponse::lmsCourseId).toList());
        assertEquals(List.of(3, 4), courses.stream().map(LmsCourseSummaryResponse::semesterNumber).toList());
    }

    @Test
    void courseIsNeverListedTwice() {
        stored.add(new LmsStudentMembership(new CourseEnrollment(semesterEnrollment, offering), lmsCourse));

        assertEquals(List.of(LMS_COURSE_ID), listedCourseIds());
    }

    @Test
    void studentWithNoMembershipsGetsAnEmptyList() {
        stored.clear();

        assertEquals(List.of(), service.getAccessibleCourses(ACCOUNT_ID));
    }

    @Test
    void anotherAccountSeesNothingOfThisStudentsCourses() {
        assertEquals(List.of(), service.getAccessibleCourses(OTHER_ACCOUNT_ID));
    }

    @Test
    void missingAccountIdListsNothingWithoutAnyLookup() {
        assertEquals(List.of(), service.getAccessibleCourses(null));

        verifyNoInteractions(memberships);
    }

    @Test
    void listingOnlyReadsAndChangesNothing() {
        membership.deactivate();
        anotherUsableCourse("TST302", 51L, 8L, semester);

        service.getAccessibleCourses(ACCOUNT_ID);

        verify(memberships).findAllWithCourseDetailsByUserAccountId(ACCOUNT_ID);
        verifyNoMoreInteractions(memberships);
        assertEquals(LmsStudentMembershipStatus.INACTIVE, membership.getStatus());
        assertEquals(StudentProfileStatus.ACTIVE, student.getStatus());
        assertEquals(SemesterEnrollmentStatus.ENROLLED, semesterEnrollment.getStatus());
        assertEquals(CourseEnrollmentStatus.ENROLLED, courseEnrollment.getStatus());
        assertEquals(LmsCourseStatus.PUBLISHED, lmsCourse.getStatus());
    }
}
