import { Route, Routes } from 'react-router'
import { LOGIN_PATH, RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'

function App() {
  return (
    <Routes>
      {/* Public: the sign-in page has its own simple layout, without the app shell. */}
      <Route path={LOGIN_PATH} element={<LoginPage />} />

      {/* Everything else, including unknown addresses, is inside the app and needs a signed-in user. */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
