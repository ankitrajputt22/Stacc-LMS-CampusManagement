import { Navigate, Outlet, useLocation } from 'react-router'
import { LoadingState } from '../components/states/LoadingState'
import { ChangePasswordPage } from '../pages/ChangePasswordPage'
import { useAuth } from './AuthContext'

export const LOGIN_PATH = '/login'

/**
 * Sends visitors who are not signed in to the sign-in page. This is for convenience only.
 * It protects nothing by itself: the backend checks every request.
 */
export function RequireAuth() {
  const { isChecking, isAuthenticated, passwordChangeRequired, signedOutByUser } = useAuth()
  const location = useLocation()

  // Until the backend has said who is signed in, show nothing protected and send nobody away.
  if (isChecking) {
    return <LoadingState label="Checking your sign-in…" />
  }

  if (!isAuthenticated) {
    // Remember where the visitor was going, so signing in can take them there. After a
    // deliberate sign-out nothing is remembered: the next person to sign in starts at home.
    const state = signedOutByUser
      ? undefined
      : { from: location.pathname + location.search + location.hash }

    return <Navigate to={LOGIN_PATH} replace state={state} />
  }

  // A required password change comes first, whatever page was asked for. The rest of the app, navigation included, is
  // left out: the backend refuses it until the password is changed, and the page asked for opens afterwards.
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
