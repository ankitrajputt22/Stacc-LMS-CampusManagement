import { Navigate, Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { AcademicAdminPage } from './pages/AcademicAdminPage'
import { AssignmentsPage } from './pages/AssignmentsPage'
import { AttendancePage } from './pages/AttendancePage'
import { CalendarPage } from './pages/CalendarPage'
import { CourseAttendancePage } from './pages/CourseAttendancePage'
import { CourseResultsPage } from './pages/CourseResultsPage'
import { CourseAssignmentPage } from './pages/CourseAssignmentPage'
import { CoursePage } from './pages/CoursePage'
import { CoursesPage } from './pages/CoursesPage'
import { DashboardPage } from './pages/DashboardPage'
import { FacultyAssignmentSubmissionsPage } from './pages/FacultyAssignmentSubmissionsPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ResultsPage } from './pages/ResultsPage'
import { StudentAttendancePage } from './pages/StudentAttendancePage'
import { StudentResultsPage } from './pages/StudentResultsPage'
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
          {/* Departments are the first section, so the bare address opens them with their link marked. */}
          <Route path="admin/academic" element={<Navigate to="/admin/academic/departments" replace />} />
          <Route path="admin/academic/:section" element={<AcademicAdminPage />} />
          <Route path="admin/academic/offerings/:courseOfferingId/enrollments" element={<AcademicAdminPage roster />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
