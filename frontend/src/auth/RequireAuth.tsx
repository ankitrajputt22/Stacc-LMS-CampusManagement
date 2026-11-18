import { Navigate, Outlet, useLocation } from 'react-router'
import { LoadingState } from '../components/states/LoadingState'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { useAuth } from './AuthContext'

export const LOGIN_PATH = '/login'

export function RequireAuth() {
  const { isChecking, isAuthenticated, passwordChangeRequired, signedOutByUser } = useAuth()
  const location = useLocation()

  if (isChecking) {
    return <LoadingState label="Checking your sign-in…" />
  }

  if (!isAuthenticated) {
    const state = signedOutByUser
      ? undefined
      : { from: location.pathname + location.search + location.hash }

    return <Navigate to={LOGIN_PATH} replace state={state} />
  }

  if (passwordChangeRequired) {
    return (
      <main className="min-h-screen bg-canvas px-4 py-10">
        <div className="mx-auto max-w-lg">
          <ChangePasswordPage required />
        </div>
      </main>
    )
  }

  return <Outlet />
}
