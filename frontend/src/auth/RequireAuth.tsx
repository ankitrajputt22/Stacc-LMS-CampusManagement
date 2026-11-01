import { Navigate, Outlet, useLocation } from 'react-router'
import { LoadingState } from '../components/states/LoadingState'
import { useAuth } from './AuthContext'

export const LOGIN_PATH = '/login'

/**
 * Sends visitors who are not signed in to the sign-in page. This is for convenience only.
 * It protects nothing by itself: the backend checks every request.
 */
export function RequireAuth() {
  const { isChecking, isAuthenticated, signedOutByUser } = useAuth()
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

  return <Outlet />
}
