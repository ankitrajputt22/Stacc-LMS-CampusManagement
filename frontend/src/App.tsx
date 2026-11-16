import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { LoadingState } from './components/states/LoadingState'
import { PageErrorBoundary } from './components/states/PageErrorBoundary'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { CourseLayout } from './courses/CourseLayout'
import { TeachingCourseLayout } from './teaching/TeachingCourseLayout'

// Pages load on first use, so the first screen downloads only what it needs.
const AcademicAdminPage = lazy(() => import('./pages/AcademicAdminPage').then((module) => ({ default: module.AcademicAdminPage })))
const ErpHomePage = lazy(() => import('./erp/ErpHomePage').then((module) => ({ default: module.ErpHomePage })))
const ErpProfilePage = lazy(() => import('./erp/ErpProfilePage').then((module) => ({ default: module.ErpProfilePage })))
const ErpTimetablePage = lazy(() => import('./erp/ErpTimetablePage').then((module) => ({ default: module.ErpTimetablePage })))
const ErpDegreeProgressPage = lazy(() => import('./erp/ErpDegreeProgressPage').then((module) => ({ default: module.ErpDegreeProgressPage })))
const ErpAttendancePage = lazy(() => import('./erp/ErpAttendancePage').then((module) => ({ default: module.ErpAttendancePage })))
const ErpCourseAttendancePage = lazy(() => import('./erp/ErpCourseAttendancePage').then((module) => ({ default: module.ErpCourseAttendancePage })))
const ErpRequestsPage = lazy(() => import('./erp/ErpRequestsPage').then((module) => ({ default: module.ErpRequestsPage })))
const ErpExamSchedulePage = lazy(() => import('./erp/ErpExamSchedulePage').then((module) => ({ default: module.ErpExamSchedulePage })))
const ErpHallTicketPage = lazy(() => import('./erp/ErpHallTicketPage').then((module) => ({ default: module.ErpHallTicketPage })))
const ErpResultsPage = lazy(() => import('./erp/ErpResultsPage').then((module) => ({ default: module.ErpResultsPage })))
const ErpGradeCardsPage = lazy(() => import('./erp/ErpGradeCardsPage').then((module) => ({ default: module.ErpGradeCardsPage })))
const ErpRevaluationPage = lazy(() => import('./erp/ErpRevaluationPage').then((module) => ({ default: module.ErpRevaluationPage })))
const ErpExamAdminPage = lazy(() => import('./erp/ErpExamAdminPage').then((module) => ({ default: module.ErpExamAdminPage })))
const ErpMarksCoursesPage = lazy(() => import('./erp/ErpMarksEntryPage').then((module) => ({ default: module.ErpMarksCoursesPage })))
const ErpMarksEntryPage = lazy(() => import('./erp/ErpMarksEntryPage').then((module) => ({ default: module.ErpMarksEntryPage })))
const ErpFeesPage = lazy(() => import('./erp/ErpFeesPage').then((module) => ({ default: module.ErpFeesPage })))
const ErpFeesAdminPage = lazy(() => import('./erp/ErpFeesAdminPage').then((module) => ({ default: module.ErpFeesAdminPage })))
const ErpAcademicsPage = lazy(() => import('./erp/ErpAcademicsPage').then((module) => ({ default: module.ErpAcademicsPage })))
const AcademicProfilePage = lazy(() => import('./pages/AcademicProfilePage').then((module) => ({ default: module.AcademicProfilePage })))
const CourseFilesPage = lazy(() => import('./pages/CourseFilesPage').then((module) => ({ default: module.CourseFilesPage })))
const CourseSyllabusPage = lazy(() => import('./pages/CourseSyllabusPage').then((module) => ({ default: module.CourseSyllabusPage })))
const LearningPage = lazy(() => import('./pages/LearningPage').then((module) => ({ default: module.LearningPage })))
const TeachingFilesPage = lazy(() => import('./pages/TeachingFilesPage').then((module) => ({ default: module.TeachingFilesPage })))
const TeachingSyllabusPage = lazy(() => import('./pages/TeachingSyllabusPage').then((module) => ({ default: module.TeachingSyllabusPage })))
const TeachingPagePreview = lazy(() => import('./pages/TeachingPagePreview').then((module) => ({ default: module.TeachingPagePreview })))
const AdminCampusPage = lazy(() => import('./pages/AdminCampusPage').then((module) => ({ default: module.AdminCampusPage })))
const AdminHostelPage = lazy(() => import('./pages/AdminHostelPage').then((module) => ({ default: module.AdminHostelPage })))
const AssignmentsPage = lazy(() => import('./pages/AssignmentsPage').then((module) => ({ default: module.AssignmentsPage })))
const AttendancePage = lazy(() => import('./pages/AttendancePage').then((module) => ({ default: module.AttendancePage })))
const CalendarPage = lazy(() => import('./pages/CalendarPage').then((module) => ({ default: module.CalendarPage })))
const CampusPage = lazy(() => import('./pages/CampusPage').then((module) => ({ default: module.CampusPage })))
const CampusRequestPage = lazy(() => import('./pages/CampusRequestPage').then((module) => ({ default: module.CampusRequestPage })))
const ChangePasswordPage = lazy(() => import('./pages/ChangePasswordPage').then((module) => ({ default: module.ChangePasswordPage })))
const CourseAttendancePage = lazy(() => import('./pages/CourseAttendancePage').then((module) => ({ default: module.CourseAttendancePage })))
const CourseResultsPage = lazy(() => import('./pages/CourseResultsPage').then((module) => ({ default: module.CourseResultsPage })))
const CourseAiPage = lazy(() => import('./pages/CourseAiPage').then((module) => ({ default: module.CourseAiPage })))
const CourseAssignmentPage = lazy(() => import('./pages/CourseAssignmentPage').then((module) => ({ default: module.CourseAssignmentPage })))
const CoursePage = lazy(() => import('./pages/CoursePage').then((module) => ({ default: module.CoursePage })))
const StudyHomePage = lazy(() => import('./pages/StudyHomePage').then((module) => ({ default: module.StudyHomePage })))
const CoursesPage = lazy(() => import('./pages/CoursesPage').then((module) => ({ default: module.CoursesPage })))
const FacultyCourseReportPage = lazy(() => import('./pages/FacultyCourseReportPage').then((module) => ({ default: module.FacultyCourseReportPage })))
const FacultyAssignmentSubmissionsPage = lazy(() => import('./pages/FacultyAssignmentSubmissionsPage').then((module) => ({ default: module.FacultyAssignmentSubmissionsPage })))
const InboxPage = lazy(() => import('./pages/InboxPage').then((module) => ({ default: module.InboxPage })))
const TodoPage = lazy(() => import('./pages/TodoPage').then((module) => ({ default: module.TodoPage })))
const StudentAnalyticsPage = lazy(() => import('./pages/StudentAnalyticsPage').then((module) => ({ default: module.StudentAnalyticsPage })))
const WardenPage = lazy(() => import('./pages/WardenPage').then((module) => ({ default: module.WardenPage })))
const GatePage = lazy(() => import('./pages/GatePage').then((module) => ({ default: module.GatePage })))
const IdCardPage = lazy(() => import('./pages/IdCardPage').then((module) => ({ default: module.IdCardPage })))
const VerifyIdPage = lazy(() => import('./pages/VerifyIdPage').then((module) => ({ default: module.VerifyIdPage })))
const VerifyDocumentPage = lazy(() => import('./pages/VerifyDocumentPage').then((module) => ({ default: module.VerifyDocumentPage })))
const DocumentsPage = lazy(() => import('./pages/DocumentsPage').then((module) => ({ default: module.DocumentsPage })))
const GrievancesPage = lazy(() => import('./pages/GrievancesPage').then((module) => ({ default: module.GrievancesPage })))
const MentoringPage = lazy(() => import('./pages/MentoringPage').then((module) => ({ default: module.MentoringPage })))
const PortfolioPage = lazy(() => import('./pages/PortfolioPage').then((module) => ({ default: module.PortfolioPage })))
const ProjectsPage = lazy(() => import('./pages/ProjectsPage').then((module) => ({ default: module.ProjectsPage })))
const InnovationPage = lazy(() => import('./pages/InnovationPage').then((module) => ({ default: module.InnovationPage })))
const HackathonsPage = lazy(() => import('./pages/HackathonsPage').then((module) => ({ default: module.HackathonsPage })))
const LibraryPage = lazy(() => import('./pages/LibraryPage').then((module) => ({ default: module.LibraryPage })))
const DepartmentAnalyticsPage = lazy(() => import('./pages/DepartmentAnalyticsPage').then((module) => ({ default: module.DepartmentAnalyticsPage })))
const AreaAnalyticsPage = lazy(() => import('./pages/AreaAnalyticsPage').then((module) => ({ default: module.AreaAnalyticsPage })))
const SystemHealthPage = lazy(() => import('./pages/SystemHealthPage').then((module) => ({ default: module.SystemHealthPage })))
const AdminMentoringPage = lazy(() => import('./pages/AdminMentoringPage').then((module) => ({ default: module.AdminMentoringPage })))
const AdminGrievancesPage = lazy(() => import('./pages/AdminGrievancesPage').then((module) => ({ default: module.AdminGrievancesPage })))
const CertificateDeskPage = lazy(() => import('./pages/CertificateDeskPage').then((module) => ({ default: module.CertificateDeskPage })))
const CareerPage = lazy(() => import('./pages/CareerPage').then((module) => ({ default: module.CareerPage })))
const PlacementPage = lazy(() => import('./pages/PlacementPage').then((module) => ({ default: module.PlacementPage })))
const NotificationSettingsPage = lazy(() => import('./pages/NotificationSettingsPage').then((module) => ({ default: module.NotificationSettingsPage })))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((module) => ({ default: module.NotFoundPage })))
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((module) => ({ default: module.ReportsPage })))
const ResultsPage = lazy(() => import('./pages/ResultsPage').then((module) => ({ default: module.ResultsPage })))
const SearchPage = lazy(() => import('./pages/SearchPage').then((module) => ({ default: module.SearchPage })))
const StudentAttendancePage = lazy(() => import('./pages/StudentAttendancePage').then((module) => ({ default: module.StudentAttendancePage })))
const StudentHostelPage = lazy(() => import('./pages/StudentHostelPage').then((module) => ({ default: module.StudentHostelPage })))
const StudentResultsPage = lazy(() => import('./pages/StudentResultsPage').then((module) => ({ default: module.StudentResultsPage })))
const CourseRegistrationPage = lazy(() => import('./pages/CourseRegistrationPage').then((module) => ({ default: module.CourseRegistrationPage })))
const AdminUserCreatePage = lazy(() => import('./pages/AdminUserCreatePage').then((module) => ({ default: module.AdminUserCreatePage })))
const AdminUserImportPage = lazy(() => import('./pages/AdminUserImportPage').then((module) => ({ default: module.AdminUserImportPage })))
const AdminUserPage = lazy(() => import('./pages/AdminUserPage').then((module) => ({ default: module.AdminUserPage })))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage').then((module) => ({ default: module.AdminUsersPage })))
const AdminAuditPage = lazy(() => import('./pages/AdminAuditPage').then((module) => ({ default: module.AdminAuditPage })))
const AccountSettingsPage = lazy(() => import('./pages/AccountSettingsPage').then((module) => ({ default: module.AccountSettingsPage })))
const ClassListPage = lazy(() => import('./pages/ClassListsPage').then((module) => ({ default: module.ClassListPage })))
const ClassListsPage = lazy(() => import('./pages/ClassListsPage').then((module) => ({ default: module.ClassListsPage })))
const SupportCategoriesPage = lazy(() => import('./pages/SupportCategoriesPage').then((module) => ({ default: module.SupportCategoriesPage })))
const SupportNewTicketPage = lazy(() => import('./pages/SupportNewTicketPage').then((module) => ({ default: module.SupportNewTicketPage })))
const SupportPage = lazy(() => import('./pages/SupportPage').then((module) => ({ default: module.SupportPage })))
const SupportTeamQueuePage = lazy(() => import('./pages/SupportTeamQueuePage').then((module) => ({ default: module.SupportTeamQueuePage })))
const SupportArticlesPage = lazy(() => import('./pages/SupportArticlesPage').then((module) => ({ default: module.SupportArticlesPage })))
const SupportTeamsPage = lazy(() => import('./pages/SupportTeamsPage').then((module) => ({ default: module.SupportTeamsPage })))
const SupportQueuePage = lazy(() => import('./pages/SupportQueuePage').then((module) => ({ default: module.SupportQueuePage })))
const SupportTicketPage = lazy(() => import('./pages/SupportTicketPage').then((module) => ({ default: module.SupportTicketPage })))
const TeachingAiPage = lazy(() => import('./pages/TeachingAiPage').then((module) => ({ default: module.TeachingAiPage })))
const TeachingAnnouncementsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingAnnouncementsPage })))
const TeachingAssignmentsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingAssignmentsPage })))
const TeachingCoursePage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingCoursePage })))
const TeachingModulesPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingModulesPage })))
const TeachingOutcomesPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingOutcomesPage })))
const TeachingQuestionBankPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingQuestionBankPage })))
const TeachingExamPaperPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingExamPaperPage })))
const TeachingExamPaperPrintPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingExamPaperPrintPage })))
const TeachingExamPapersPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingExamPapersPage })))
const TeachingGradebookPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingGradebookPage })))
const TeachingGradeItemPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingGradeItemPage })))
const TeachingQuizAttemptPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingQuizAttemptPage })))
const TeachingQuizPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingQuizPage })))
const TeachingQuizResultsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingQuizResultsPage })))
const TeachingQuizzesPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingQuizzesPage })))
const TeachingRubricsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingRubricsPage })))
const TeachingAnalyticsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingAnalyticsPage })))
const TeachingDiscussionsPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingDiscussionsPage })))
const TeachingDiscussionPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingDiscussionPage })))
const TeachingMessagesPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingMessagesPage })))
const TeachingConversationPage = lazy(() => import('./pages/TeachingCoursePage').then((module) => ({ default: module.TeachingConversationPage })))
const TeachingPage = lazy(() => import('./pages/TeachingPage').then((module) => ({ default: module.TeachingPage })))
const CourseConversationPage = lazy(() => import('./pages/CourseCommunicationPages').then((module) => ({ default: module.CourseConversationPage })))
const CourseDiscussionPage = lazy(() => import('./pages/CourseCommunicationPages').then((module) => ({ default: module.CourseDiscussionPage })))
const CourseDiscussionsPage = lazy(() => import('./pages/CourseCommunicationPages').then((module) => ({ default: module.CourseDiscussionsPage })))
const CourseMessagesPage = lazy(() => import('./pages/CourseCommunicationPages').then((module) => ({ default: module.CourseMessagesPage })))
const CourseAnnouncementsPage = lazy(() => import('./pages/CourseSectionPages').then((module) => ({ default: module.CourseAnnouncementsPage })))
const CourseAssignmentsPage = lazy(() => import('./pages/CourseSectionPages').then((module) => ({ default: module.CourseAssignmentsPage })))
const CourseModulesPage = lazy(() => import('./pages/CourseSectionPages').then((module) => ({ default: module.CourseModulesPage })))
const CoursePagesIndexPage = lazy(() => import('./pages/CourseSectionPages').then((module) => ({ default: module.CoursePagesIndexPage })))
const CourseGradesPage = lazy(() => import('./pages/CourseQuizPages').then((module) => ({ default: module.CourseGradesPage })))
const CourseQuizPage = lazy(() => import('./pages/CourseQuizPages').then((module) => ({ default: module.CourseQuizPage })))
const CourseQuizzesPage = lazy(() => import('./pages/CourseQuizPages').then((module) => ({ default: module.CourseQuizzesPage })))
const QuizAttemptPage = lazy(() => import('./pages/CourseQuizPages').then((module) => ({ default: module.QuizAttemptPage })))

function App() {
  return (
    <PageErrorBoundary>
    <Suspense fallback={<LoadingState />}>
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
          <Route path="study" element={<StudyHomePage />} />
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
            <Route path="ai/:section" element={<CourseAiPage />} />
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
            <Route path="ai/:section" element={<TeachingAiPage />} />
          </Route>
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="attendance" element={<AttendancePage />} />
          <Route path="attendance/:courseOfferingId" element={<CourseAttendancePage />} />
          <Route path="erp" element={<ErpHomePage />} />
          <Route path="erp/profile" element={<ErpProfilePage />} />
          <Route path="erp/academics" element={<ErpAcademicsPage />} />
          <Route path="erp/academics/timetable" element={<ErpTimetablePage />} />
          <Route path="erp/academics/degree-progress" element={<ErpDegreeProgressPage />} />
          <Route path="erp/timetable" element={<ErpTimetablePage teaching />} />
          <Route path="erp/attendance" element={<ErpAttendancePage />} />
          <Route path="erp/attendance/:courseOfferingId" element={<ErpCourseAttendancePage />} />
          <Route path="erp/requests" element={<ErpRequestsPage />} />
          <Route path="erp/exams" element={<ErpExamSchedulePage />} />
          <Route path="erp/exams/hall-ticket" element={<ErpHallTicketPage />} />
          <Route path="erp/exams/results" element={<ErpResultsPage />} />
          <Route path="erp/exams/grade-cards" element={<ErpGradeCardsPage />} />
          <Route path="erp/exams/revaluation" element={<ErpRevaluationPage />} />
          <Route path="erp/exam-admin" element={<ErpExamAdminPage />} />
          <Route path="erp/fees" element={<ErpFeesPage />} />
          <Route path="erp/fees/structure" element={<ErpFeesPage tab="structure" />} />
          <Route path="erp/fees/payments" element={<ErpFeesPage tab="payments" />} />
          <Route path="erp/fees/scholarships" element={<ErpFeesPage tab="scholarships" />} />
          <Route path="erp/fees/refunds" element={<ErpFeesPage tab="refunds" />} />
          <Route path="erp/fees-admin" element={<ErpFeesAdminPage />} />
          <Route path="erp/marks" element={<ErpMarksCoursesPage />} />
          <Route path="erp/marks/:courseOfferingId" element={<ErpMarksEntryPage />} />
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
          <Route path="warden/roll-call" element={<WardenPage section="roll-call" />} />
          <Route path="warden/notices" element={<WardenPage section="notices" />} />
          <Route path="warden/mess" element={<WardenPage section="mess" />} />
          <Route path="warden/bed-map" element={<WardenPage section="bed-map" />} />
          <Route path="gate" element={<GatePage />} />
          <Route path="id-card" element={<IdCardPage />} />
          <Route path="verify-id" element={<VerifyIdPage />} />
          <Route path="documents" element={<DocumentsPage />} />
          <Route path="grievances" element={<GrievancesPage />} />
          <Route path="grievances/new" element={<GrievancesPage section="new" />} />
          <Route path="grievances/committee" element={<GrievancesPage section="committee" />} />
          <Route path="grievances/committee/:caseId" element={<GrievancesPage section="committeeCase" />} />
          <Route path="grievances/:caseId" element={<GrievancesPage section="myCase" />} />
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
          <Route path="hostel/applications" element={<StudentHostelPage section="applications" />} />
          <Route path="hostel/room-changes" element={<StudentHostelPage section="room-changes" />} />
          <Route path="hostel/outings" element={<StudentHostelPage section="outings" />} />
          <Route path="hostel/mess" element={<StudentHostelPage section="mess" />} />
          <Route path="hostel/visitors" element={<StudentHostelPage section="visitors" />} />
          <Route path="hostel/room-problem" element={<StudentHostelPage section="room-problem" />} />
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
    </Suspense>
    </PageErrorBoundary>
  )
}

export default App
