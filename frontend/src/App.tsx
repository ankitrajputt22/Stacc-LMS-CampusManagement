import { Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { AssignmentsPage } from './pages/AssignmentsPage'
import { CourseAssignmentPage } from './pages/CourseAssignmentPage'
import { CoursePage } from './pages/CoursePage'
import { CoursesPage } from './pages/CoursesPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
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
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
