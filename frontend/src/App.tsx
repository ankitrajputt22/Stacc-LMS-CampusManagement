import { Navigate, Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { AcademicAdminPage } from './pages/AcademicAdminPage'
import { AcademicProfilePage } from './pages/AcademicProfilePage'
import { CourseFilesPage } from './pages/CourseFilesPage'
import { CourseSyllabusPage } from './pages/CourseSyllabusPage'
import { LearningPage } from './pages/LearningPage'
import { TeachingFilesPage } from './pages/TeachingFilesPage'
import { TeachingSyllabusPage } from './pages/TeachingSyllabusPage'
import { TeachingPagePreview } from './pages/TeachingPagePreview'
import { AdminCampusPage } from './pages/AdminCampusPage'
import { AdminHostelPage } from './pages/AdminHostelPage'
import { AssignmentsPage } from './pages/AssignmentsPage'
import { AttendancePage } from './pages/AttendancePage'
import { CalendarPage } from './pages/CalendarPage'
import { CampusPage } from './pages/CampusPage'
import { CampusRequestPage } from './pages/CampusRequestPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { CourseAttendancePage } from './pages/CourseAttendancePage'
import { CourseResultsPage } from './pages/CourseResultsPage'
import { CourseAiPage } from './pages/CourseAiPage'
import { CourseAssignmentPage } from './pages/CourseAssignmentPage'
import { CoursePage } from './pages/CoursePage'
import { CoursesPage } from './pages/CoursesPage'
import { DashboardPage } from './pages/DashboardPage'
import { FacultyCourseReportPage } from './pages/FacultyCourseReportPage'
import { FacultyAssignmentSubmissionsPage } from './pages/FacultyAssignmentSubmissionsPage'
import { InboxPage } from './pages/InboxPage'
import { TodoPage } from './pages/TodoPage'
import { StudentAnalyticsPage } from './pages/StudentAnalyticsPage'
import { WardenPage } from './pages/WardenPage'
import { GatePage } from './pages/GatePage'
import { IdCardPage } from './pages/IdCardPage'
import { VerifyIdPage } from './pages/VerifyIdPage'
import { VerifyDocumentPage } from './pages/VerifyDocumentPage'
import { DocumentsPage } from './pages/DocumentsPage'
import { GrievancesPage } from './pages/GrievancesPage'
import { MentoringPage } from './pages/MentoringPage'
import { PortfolioPage } from './pages/PortfolioPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { InnovationPage } from './pages/InnovationPage'
import { HackathonsPage } from './pages/HackathonsPage'
import { LibraryPage } from './pages/LibraryPage'
import { DepartmentAnalyticsPage } from './pages/DepartmentAnalyticsPage'
import { AreaAnalyticsPage } from './pages/AreaAnalyticsPage'
import { SystemHealthPage } from './pages/SystemHealthPage'
import { AdminMentoringPage } from './pages/AdminMentoringPage'
import { AdminGrievancesPage } from './pages/AdminGrievancesPage'
import { CertificateDeskPage } from './pages/CertificateDeskPage'
import { CareerPage } from './pages/CareerPage'
import { PlacementPage } from './pages/PlacementPage'
import { NotificationSettingsPage } from './pages/NotificationSettingsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ReportsPage } from './pages/ReportsPage'
import { ResultsPage } from './pages/ResultsPage'
import { SearchPage } from './pages/SearchPage'
import { StudentAttendancePage } from './pages/StudentAttendancePage'
import { StudentHostelPage } from './pages/StudentHostelPage'
import { StudentResultsPage } from './pages/StudentResultsPage'
import { CourseRegistrationPage } from './pages/CourseRegistrationPage'
import { AdminUserCreatePage } from './pages/AdminUserCreatePage'
import { AdminUserImportPage } from './pages/AdminUserImportPage'
import { AdminUserPage } from './pages/AdminUserPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { AdminAuditPage } from './pages/AdminAuditPage'
import { AccountSettingsPage } from './pages/AccountSettingsPage'
import { ClassListPage, ClassListsPage } from './pages/ClassListsPage'
import { SupportCategoriesPage } from './pages/SupportCategoriesPage'
import { SupportNewTicketPage } from './pages/SupportNewTicketPage'
import { SupportPage } from './pages/SupportPage'
import { SupportTeamQueuePage } from './pages/SupportTeamQueuePage'
import { SupportArticlesPage } from './pages/SupportArticlesPage'
import { SupportTeamsPage } from './pages/SupportTeamsPage'
import { SupportQueuePage } from './pages/SupportQueuePage'
import { SupportTicketPage } from './pages/SupportTicketPage'
import { TeachingAiPage } from './pages/TeachingAiPage'
import {
  TeachingAnnouncementsPage,
  TeachingAssignmentsPage,
  TeachingCoursePage,
  TeachingModulesPage,
  TeachingOutcomesPage,
  TeachingQuestionBankPage,
  TeachingExamPaperPage,
  TeachingExamPaperPrintPage,
  TeachingExamPapersPage,
  TeachingGradebookPage,
  TeachingGradeItemPage,
  TeachingQuizAttemptPage,
  TeachingQuizPage,
  TeachingQuizResultsPage,
  TeachingQuizzesPage,
  TeachingRubricsPage,
  TeachingAnalyticsPage,
  TeachingDiscussionsPage,
  TeachingDiscussionPage,
  TeachingMessagesPage,
  TeachingConversationPage,
} from './pages/TeachingCoursePage'
import { TeachingPage } from './pages/TeachingPage'
import { CourseLayout } from './courses/CourseLayout'
import {
  CourseConversationPage,
  CourseDiscussionPage,
  CourseDiscussionsPage,
  CourseMessagesPage,
} from './pages/CourseCommunicationPages'
import {
  CourseAnnouncementsPage,
  CourseAssignmentsPage,
  CourseModulesPage,
  CoursePagesIndexPage,
} from './pages/CourseSectionPages'
import { TeachingCourseLayout } from './teaching/TeachingCourseLayout'
import { CourseGradesPage, CourseQuizPage, CourseQuizzesPage, QuizAttemptPage } from './pages/CourseQuizPages'

function App() {
  return (
    <Routes>
      {/* Public: the sign-in page has its own simple layout, without the app shell. */}
      <Route path={LOGIN_PATH} element={<LoginPage />} />
      {/* Public: anyone given a verification code can check a document is authentic. */}
      <Route path="verify-document" element={<VerifyDocumentPage />} />

      {/* Everything else, including unknown addresses, is inside the app and needs a signed-in user. */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="courses" element={<CoursesPage />} />
          {/* Every page of one course shares the course navigation. */}
          <Route path="courses/:lmsCourseId" element={<CourseLayout />}>
            <Route index element={<CoursePage />} />
            <Route path="modules" element={<CourseModulesPage />} />
            <Route path="pages" element={<CoursePagesIndexPage />} />
            <Route path="pages/:pageId" element={<LearningPage />} />
            <Route path="assignments" element={<CourseAssignmentsPage />} />
            <Route path="assignments/:assignmentId" element={<CourseAssignmentPage />} />
            <Route path="quizzes" element={<CourseQuizzesPage />} />
            <Route path="quizzes/:quizId" element={<CourseQuizPage />} />
            <Route path="quizzes/:quizId/attempts/:attemptId" element={<QuizAttemptPage />} />
            <Route path="grades" element={<CourseGradesPage />} />
            <Route path="announcements" element={<CourseAnnouncementsPage />} />
            <Route path="discussions" element={<CourseDiscussionsPage />} />
            <Route path="discussions/:threadId" element={<CourseDiscussionPage />} />
            <Route path="messages" element={<CourseMessagesPage />} />
            <Route path="messages/:conversationId" element={<CourseConversationPage />} />
            <Route path="files" element={<CourseFilesPage />} />
            <Route path="syllabus" element={<CourseSyllabusPage />} />
            <Route path="ai" element={<CourseAiPage />} />
          </Route>
          <Route path="teaching" element={<TeachingPage />} />
          <Route path="teaching/:lmsCourseId" element={<TeachingCourseLayout />}>
            <Route index element={<TeachingCoursePage />} />
            <Route path="modules" element={<TeachingModulesPage />} />
            <Route path="announcements" element={<TeachingAnnouncementsPage />} />
            <Route path="discussions" element={<TeachingDiscussionsPage />} />
            <Route path="discussions/:threadId" element={<TeachingDiscussionPage />} />
            <Route path="messages" element={<TeachingMessagesPage />} />
            <Route path="messages/:conversationId" element={<TeachingConversationPage />} />
            <Route path="assignments" element={<TeachingAssignmentsPage />} />
            <Route path="outcomes" element={<TeachingOutcomesPage />} />
            <Route path="quizzes" element={<TeachingQuizzesPage />} />
            <Route path="quizzes/:quizId" element={<TeachingQuizPage />} />
            <Route path="quizzes/:quizId/results" element={<TeachingQuizResultsPage />} />
            <Route path="quiz-attempts/:attemptId" element={<TeachingQuizAttemptPage />} />
            <Route path="gradebook" element={<TeachingGradebookPage />} />
            <Route path="analytics" element={<TeachingAnalyticsPage />} />
            <Route path="rubrics" element={<TeachingRubricsPage />} />
            <Route path="gradebook/items/:itemId" element={<TeachingGradeItemPage />} />
            <Route path="questions" element={<TeachingQuestionBankPage />} />
            <Route path="papers" element={<TeachingExamPapersPage />} />
            <Route path="papers/:paperId" element={<TeachingExamPaperPage />} />
            <Route path="papers/:paperId/print" element={<TeachingExamPaperPrintPage />} />
            <Route path="assignments/:assignmentId/submissions" element={<FacultyAssignmentSubmissionsPage />} />
            <Route path="pages/:pageId" element={<TeachingPagePreview />} />
            <Route path="files" element={<TeachingFilesPage />} />
            <Route path="syllabus" element={<TeachingSyllabusPage />} />
            <Route path="ai" element={<TeachingAiPage />} />
          </Route>
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="attendance" element={<AttendancePage />} />
          <Route path="attendance/:courseOfferingId" element={<CourseAttendancePage />} />
          <Route path="academic/attendance" element={<StudentAttendancePage />} />
          <Route path="academic/results" element={<StudentResultsPage />} />
          <Route path="academic/analytics" element={<StudentAnalyticsPage />} />
          <Route path="academic/registration" element={<CourseRegistrationPage />} />
          <Route path="academic/profile" element={<AcademicProfilePage />} />
          <Route path="results" element={<ResultsPage />} />
          <Route path="admin/users" element={<AdminUsersPage />} />
          <Route path="admin/audit" element={<AdminAuditPage />} />
          <Route path="admin/users/new" element={<AdminUserCreatePage />} />
          <Route path="admin/users/import" element={<AdminUserImportPage />} />
          <Route path="admin/users/:accountId" element={<AdminUserPage />} />
          <Route path="class-lists" element={<ClassListsPage />} />
          <Route path="class-lists/:courseOfferingId" element={<ClassListPage />} />
          <Route path="results/:courseOfferingId" element={<CourseResultsPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          {/* One search page for every role; the address holds the query, and View all the kind and page. */}
          <Route path="search" element={<SearchPage />} />
          {/* Reports: one page with a part per role (faculty, admin), and one taught offering's report. */}
          <Route path="reports" element={<ReportsPage />} />
          <Route path="reports/faculty/course-offerings/:courseOfferingId" element={<FacultyCourseReportPage />} />
          <Route path="inbox" element={<InboxPage />} />
          <Route path="todo" element={<TodoPage />} />
          <Route path="warden" element={<WardenPage />} />
          <Route path="gate" element={<GatePage />} />
          <Route path="id-card" element={<IdCardPage />} />
          <Route path="verify-id" element={<VerifyIdPage />} />
          <Route path="documents" element={<DocumentsPage />} />
          <Route path="grievances" element={<GrievancesPage />} />
          <Route path="admin/grievances" element={<AdminGrievancesPage />} />
          <Route path="mentoring" element={<MentoringPage />} />
          <Route path="portfolio" element={<PortfolioPage />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="innovation" element={<InnovationPage />} />
          <Route path="hackathons" element={<HackathonsPage />} />
          <Route path="library" element={<LibraryPage />} />
          <Route path="department" element={<DepartmentAnalyticsPage />} />
          <Route path="areas" element={<AreaAnalyticsPage />} />
          <Route path="admin/system" element={<SystemHealthPage />} />
          <Route path="admin/mentoring" element={<AdminMentoringPage />} />
          <Route path="admin/certificates" element={<CertificateDeskPage />} />
          <Route path="career" element={<CareerPage />} />
          <Route path="placement" element={<PlacementPage />} />
          <Route path="inbox/settings" element={<NotificationSettingsPage />} />
          <Route path="account" element={<AccountSettingsPage />} />
          <Route path="account/password" element={<ChangePasswordPage />} />
          {/* One Support area for every account: requester pages, the shared ticket page, and the admin pages. */}
          <Route path="support" element={<SupportPage />} />
          <Route path="support/new" element={<SupportNewTicketPage />} />
          <Route path="support/tickets/:ticketId" element={<SupportTicketPage />} />
          <Route path="support/admin" element={<SupportQueuePage />} />
          <Route path="support/admin/categories" element={<SupportCategoriesPage />} />
          <Route path="support/admin/teams" element={<SupportTeamsPage />} />
          <Route path="support/admin/articles" element={<SupportArticlesPage />} />
          <Route path="support/team" element={<SupportTeamQueuePage />} />
          {/* Departments are the first section, so the bare address opens them with their link marked. */}
          <Route path="admin/academic" element={<Navigate to="/admin/academic/departments" replace />} />
          <Route path="admin/academic/:section" element={<AcademicAdminPage />} />
          {/* Hostel: the student's page, and the hostel office's sections; the applications section opens first. */}
          <Route path="hostel" element={<StudentHostelPage />} />
          <Route path="admin/hostel" element={<Navigate to="/admin/hostel/applications" replace />} />
          <Route path="admin/hostel/:section" element={<AdminHostelPage />} />
          <Route path="admin/hostel/applications/:applicationId" element={<AdminHostelPage review />} />
          {/* Campus: one page for every role, a request page for its requester, and Campus Management for admins. */}
          <Route path="campus" element={<CampusPage />} />
          <Route path="campus/:tab" element={<CampusPage />} />
          <Route path="campus/requests/:requestId" element={<CampusRequestPage />} />
          <Route path="admin/campus" element={<Navigate to="/admin/campus/services" replace />} />
          <Route path="admin/campus/:section" element={<AdminCampusPage />} />
          <Route path="admin/campus/requests/:requestId" element={<AdminCampusPage review />} />
          <Route path="admin/academic/offerings/:courseOfferingId/enrollments" element={<AcademicAdminPage roster />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
