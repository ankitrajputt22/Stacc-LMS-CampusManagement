import { Navigate, Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { AcademicAdminPage } from './pages/AcademicAdminPage'
import { AdminCampusPage } from './pages/AdminCampusPage'
import { AdminHostelPage } from './pages/AdminHostelPage'
import { AssignmentsPage } from './pages/AssignmentsPage'
import { AttendancePage } from './pages/AttendancePage'
import { CalendarPage } from './pages/CalendarPage'
import { CampusPage } from './pages/CampusPage'
import { CampusRequestPage } from './pages/CampusRequestPage'
import { CourseAttendancePage } from './pages/CourseAttendancePage'
import { CourseResultsPage } from './pages/CourseResultsPage'
import { CourseAssignmentPage } from './pages/CourseAssignmentPage'
import { CoursePage } from './pages/CoursePage'
import { CoursesPage } from './pages/CoursesPage'
import { DashboardPage } from './pages/DashboardPage'
import { FacultyAssignmentSubmissionsPage } from './pages/FacultyAssignmentSubmissionsPage'
import { InboxPage } from './pages/InboxPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ResultsPage } from './pages/ResultsPage'
import { StudentAttendancePage } from './pages/StudentAttendancePage'
import { StudentHostelPage } from './pages/StudentHostelPage'
import { StudentResultsPage } from './pages/StudentResultsPage'
import { SupportCategoriesPage } from './pages/SupportCategoriesPage'
import { SupportNewTicketPage } from './pages/SupportNewTicketPage'
import { SupportPage } from './pages/SupportPage'
import { SupportQueuePage } from './pages/SupportQueuePage'
import { SupportTicketPage } from './pages/SupportTicketPage'
import { TeachingCoursePage } from './pages/TeachingCoursePage'
import { TeachingPage } from './pages/TeachingPage'

function App() {
  return (
    <Routes>
      {/* Public: the sign-in page has its own simple layout, without the app shell. */}
      <Route path={LOGIN_PATH} element={<LoginPage />} />

      {/* Everything else, including unknown addresses, is inside the app and needs a signed-in user. */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="courses" element={<CoursesPage />} />
          <Route path="courses/:lmsCourseId" element={<CoursePage />} />
          <Route path="courses/:lmsCourseId/assignments/:assignmentId" element={<CourseAssignmentPage />} />
          <Route path="teaching" element={<TeachingPage />} />
          <Route path="teaching/:lmsCourseId" element={<TeachingCoursePage />} />
          <Route
            path="teaching/:lmsCourseId/assignments/:assignmentId/submissions"
            element={<FacultyAssignmentSubmissionsPage />}
          />
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="attendance" element={<AttendancePage />} />
          <Route path="attendance/:courseOfferingId" element={<CourseAttendancePage />} />
          <Route path="academic/attendance" element={<StudentAttendancePage />} />
          <Route path="academic/results" element={<StudentResultsPage />} />
          <Route path="results" element={<ResultsPage />} />
          <Route path="results/:courseOfferingId" element={<CourseResultsPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="inbox" element={<InboxPage />} />
          {/* One Support area for every account: requester pages, the shared ticket page, and the admin pages. */}
          <Route path="support" element={<SupportPage />} />
          <Route path="support/new" element={<SupportNewTicketPage />} />
          <Route path="support/tickets/:ticketId" element={<SupportTicketPage />} />
          <Route path="support/admin" element={<SupportQueuePage />} />
          <Route path="support/admin/categories" element={<SupportCategoriesPage />} />
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
