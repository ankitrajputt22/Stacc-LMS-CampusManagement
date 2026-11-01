import axios, { isAxiosError } from 'axios'
import { clearSession, loadSession } from '../auth/authStorage'

const REQUEST_TIMEOUT_MS = 15000

/** The API version this frontend speaks. It is part of the code, not of the deployment settings. */
export const API_VERSION = 'v1'

const apiRoot = import.meta.env.VITE_API_BASE_URL

if (!apiRoot) {
  throw new Error(
    'VITE_API_BASE_URL is not set. Copy frontend/.env.example to frontend/.env.',
  )
}

/**
 * Where versioned requests go: the configured API root (such as `/api` or `http://localhost:8080/api`) plus the
 * version, so `/lms/my-courses` becomes `/api/v1/lms/my-courses`.
 */
export const apiBaseUrl = `${apiRoot.replace(/\/+$/, '')}/${API_VERSION}`

// Axios sends plain objects as JSON on its own, so no default Content-Type is set here.
export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: REQUEST_TIMEOUT_MS,
  headers: {
    Accept: 'application/json',
  },
})

type UnauthorizedListener = () => void

const unauthorizedListeners = new Set<UnauthorizedListener>()

/**
 * Calls the listener when the backend refuses the saved access token.
 * Returns a function that stops listening.
 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)

  return () => {
    unauthorizedListeners.delete(listener)
  }
}

function bearer(accessToken: string): string {
  return `Bearer ${accessToken}`
}

// Every request made while signed in carries the access token. Nothing is added when signed
// out, so API functions never set this header themselves.
apiClient.interceptors.request.use((config) => {
  const session = loadSession()

  if (session !== null) {
    config.headers.set('Authorization', bearer(session.accessToken))
  }

  return config
})

// A 401 answer to a request that carried the current token means the backend no longer
// accepts it, so the session is dropped and the app is told. Two cases are left alone on
// purpose: a failed login, which carries no token and is an ordinary form error, and a 403,
// which means "signed in but not allowed" and must not sign anyone out.
apiClient.interceptors.response.use(undefined, (error: unknown) => {
  if (isAxiosError(error) && error.response?.status === 401) {
    const session = loadSession()
    const sentToken = error.config?.headers.get('Authorization')

    if (session !== null && sentToken === bearer(session.accessToken)) {
      clearSession()
      unauthorizedListeners.forEach((listener) => listener())
    }
  }

  return Promise.reject(error)
})
