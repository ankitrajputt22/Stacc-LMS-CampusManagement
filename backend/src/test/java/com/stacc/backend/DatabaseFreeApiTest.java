package com.stacc.backend;

import com.stacc.backend.lms.discussion.LmsDiscussionReplyRepository;
import com.stacc.backend.lms.discussion.LmsDiscussionThreadRepository;
import com.stacc.backend.lms.message.LmsConversationMessageRepository;
import com.stacc.backend.lms.message.LmsConversationRepository;
import com.stacc.backend.lms.message.LmsMessageAttachmentRepository;
import com.stacc.backend.calendar.personal.CalendarFeedLinkRepository;
import com.stacc.backend.calendar.personal.PersonalCalendarEventRepository;
import com.stacc.backend.notification.NotificationDigestItemRepository;
import com.stacc.backend.notification.NotificationPreferenceRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTeamMemberRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTeamRepository;
import com.stacc.backend.hostel.HostelWardenRepository;
import com.stacc.backend.documents.VaultDocumentRepository;
import com.stacc.backend.documents.CertificateRequestRepository;
import com.stacc.backend.career.CareerProfileRepository;
import com.stacc.backend.career.CareerResumeRepository;
import com.stacc.backend.career.CareerCompanyRepository;
import com.stacc.backend.career.CareerOpportunityRepository;
import com.stacc.backend.career.CareerRoundRepository;
import com.stacc.backend.career.CareerApplicationRepository;
import com.stacc.backend.career.CareerOfferRepository;
import com.stacc.backend.career.CareerPolicyRepository;
import com.stacc.backend.engagement.groups.CampusGroupRepository;
import com.stacc.backend.engagement.groups.CampusGroupOfficerRepository;
import com.stacc.backend.engagement.groups.CampusGroupMembershipRepository;
import com.stacc.backend.engagement.facilities.CampusFacilityRepository;
import com.stacc.backend.engagement.facilities.CampusFacilityClosureRepository;
import com.stacc.backend.engagement.facilities.CampusBookingRepository;
import com.stacc.backend.engagement.events.CampusEventRepository;
import com.stacc.backend.engagement.events.CampusEventRegistrationRepository;
import com.stacc.backend.hostel.outing.GateMovementRepository;
import com.stacc.backend.hostel.outing.HostelOutingExtensionRepository;
import com.stacc.backend.hostel.outing.HostelOutingRepository;
import com.stacc.backend.pass.PassRepository;
import com.stacc.backend.todo.TodoAssignmentQueries;
import com.stacc.backend.todo.TodoDismissalRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stacc.backend.approval.ApprovalRequestHistoryRepository;
import com.stacc.backend.approval.ApprovalRequestRepository;
import com.stacc.backend.approval.ApprovalRequestStepRepository;
import com.stacc.backend.approval.ApprovalWorkflowDefinitionRepository;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.responsibility.DepartmentHeadRepository;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.lms.content.LmsPageCompletionRepository;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import com.stacc.backend.lms.assignment.rubric.LmsRubricRepository;
import com.stacc.backend.lms.gradebook.LmsGradeEntryRepository;
import com.stacc.backend.lms.gradebook.LmsGradeItemRepository;
import com.stacc.backend.lms.question.LmsQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizQuestionRepository;
import com.stacc.backend.lms.quiz.LmsQuizRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswerMarkRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAnswerRepository;
import com.stacc.backend.lms.quiz.attempt.LmsQuizAttemptRepository;
import com.stacc.backend.lms.syllabus.LmsSyllabusRepository;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.auth.role.RoleRepository;
import com.stacc.backend.auth.session.SessionProperties;
import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.campus.notices.CampusNoticeRepository;
import com.stacc.backend.campus.services.CampusServiceDefinitionRepository;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import com.stacc.backend.common.web.RequestIdFilter;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelApplicationRepository;
import com.stacc.backend.hostel.HostelBedRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelRoomRepository;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfileRepository;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.lms.ai.AiCourseKnowledgeIndexRepository;
import com.stacc.backend.lms.announcement.LmsAnnouncementRepository;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.content.LmsModuleRepository;
import com.stacc.backend.lms.content.LmsStudyMaterialRepository;
import com.stacc.backend.lms.content.LmsTopicRepository;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.notification.NotificationRecipientRepository;
import com.stacc.backend.notification.NotificationRepository;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketHistoryRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketMessageRepository;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import jakarta.servlet.Filter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for tests that call the API through the whole application without a database,
 * so they need no MySQL credentials. The account repository is replaced by a stand-in
 * that each test can prepare, and tokens are signed with a made-up test secret. Requests
 * pass through the Spring Security filter chain, as they do in the running app.
 *
 * <p>Without a database there are no real repositories. A service that needs repositories
 * can only start here if they are listed below as stand-ins, even when no test uses them.
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "stacc.jwt.secret=" + TestSecrets.JWT_SECRET,
    // Without a database there is no "db" health check, which the readiness group names.
    "management.endpoint.health.validate-group-membership=false"
})
@MockitoBean(types = {
    AcademicCalendarEventRepository.class,
    AiCourseKnowledgeIndexRepository.class,
    AcademicSessionRepository.class,
    com.stacc.backend.identity.admin.AccountSearchRepository.class,
    com.stacc.backend.auth.throttle.SignInThrottleRepository.class,
    com.stacc.backend.audit.AuditEventRepository.class,
    ApprovalRequestHistoryRepository.class,
    ApprovalRequestRepository.class,
    ApprovalRequestStepRepository.class,
    ApprovalWorkflowDefinitionRepository.class,
    AttendanceRecordRepository.class,
    AttendanceSessionRepository.class,
    CampusNoticeRepository.class,
    CampusServiceDefinitionRepository.class,
    CampusServiceRequestRepository.class,
    CourseEnrollmentRepository.class,
    CourseOfferingRepository.class,
    CourseRepository.class,
    CourseResultRepository.class,
    DepartmentRepository.class,
    FacultyProfileRepository.class,
    HostelAllocationRepository.class,
    HostelApplicationRepository.class,
    HostelBedRepository.class,
    HostelRepository.class,
    HostelRoomRepository.class,
    HelpdeskCategoryRepository.class,
    HelpdeskTicketHistoryRepository.class,
    HelpdeskTicketMessageRepository.class,
    HelpdeskTicketRepository.class,
    LmsAnnouncementRepository.class,
    LmsAssignmentRepository.class,
    LmsAssignmentSubmissionRepository.class,
    LmsCourseRepository.class,
    LmsModuleRepository.class,
    LmsStudyMaterialRepository.class,
    LmsSubmissionAttachmentRepository.class,
    LmsSubmissionAttemptRepository.class,
    LmsSubmissionGradeRepository.class,
    LmsTopicRepository.class,
    NotificationRecipientRepository.class,
    NotificationRepository.class,
    PersonProfileRepository.class,
    ProgramRepository.class,
    RoleRepository.class,
    SectionRepository.class,
    SemesterEnrollmentRepository.class,
    SemesterRepository.class,
    StudentProfileRepository.class,
    TeachingAssignmentRepository.class,
    DepartmentHeadRepository.class,
    LmsSyllabusRepository.class,
    LmsPageCompletionRepository.class,
    LmsCourseOutcomeRepository.class,
    LmsQuestionRepository.class,
    LmsQuizRepository.class,
    LmsQuizQuestionRepository.class,
    LmsQuizAttemptRepository.class,
    LmsQuizAnswerRepository.class,
    LmsQuizAnswerMarkRepository.class,
    LmsGradeItemRepository.class,
    LmsGradeEntryRepository.class,
    LmsRubricRepository.class,
    LmsDiscussionThreadRepository.class,
    LmsDiscussionReplyRepository.class,
    LmsConversationRepository.class,
    LmsConversationMessageRepository.class,
    LmsMessageAttachmentRepository.class,
    TodoDismissalRepository.class,
    TodoAssignmentQueries.class,
    PersonalCalendarEventRepository.class,
    CalendarFeedLinkRepository.class,
    NotificationPreferenceRepository.class,
    NotificationDigestItemRepository.class,
    HelpdeskTeamRepository.class,
    HelpdeskTeamMemberRepository.class,
    HostelWardenRepository.class,
    PassRepository.class,
    HostelOutingRepository.class,
    HostelOutingExtensionRepository.class,
    GateMovementRepository.class,
    CampusGroupRepository.class,
    CampusGroupOfficerRepository.class,
    CampusGroupMembershipRepository.class,
    CampusFacilityRepository.class,
    CampusFacilityClosureRepository.class,
    CampusBookingRepository.class,
    CampusEventRepository.class,
    CampusEventRegistrationRepository.class,
    VaultDocumentRepository.class,
    CertificateRequestRepository.class,
    CareerProfileRepository.class,
    CareerResumeRepository.class,
    CareerCompanyRepository.class,
    CareerOpportunityRepository.class,
    CareerRoundRepository.class,
    CareerApplicationRepository.class,
    CareerOfferRepository.class,
    CareerPolicyRepository.class
})
public abstract class DatabaseFreeApiTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private RequestIdFilter requestIdFilter;

    @MockitoBean
    protected UserAccountRepository userAccountRepository;

    /** Sessions are open by default here, so tokens from TestTokens are accepted; a test can say otherwise. */
    @MockitoBean
    protected SessionService sessionService;

    protected MockMvc mockMvc;

    @BeforeEach
    void openTestSessions() {
        when(sessionService.check(any(), any())).thenReturn(new SessionService.SessionCheck(true, false));
        when(sessionService.properties()).thenReturn(
                new SessionProperties(Duration.ofHours(12), Duration.ofHours(2), false));
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter, springSecurityFilterChain)
                .build();
    }
}
