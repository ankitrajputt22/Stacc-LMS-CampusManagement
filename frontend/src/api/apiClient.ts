import axios, { isAxiosError } from 'axios'

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
//
// Signing in is a cookie the page never sees: the backend sets an HttpOnly session cookie, and the browser sends it
// with every API request (withCredentials covers a dev server on another port). A change must also carry the
// XSRF-TOKEN cookie's value in the X-XSRF-TOKEN header, which Axios copies itself; only Stacc's own pages can read that
// cookie, so another website cannot forge the request. Nothing here sets an Authorization header.
export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: REQUEST_TIMEOUT_MS,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    Accept: 'application/json',
  },
})

type UnauthorizedListener = () => void

const unauthorizedListeners = new Set<UnauthorizedListener>()

// Answers from these say only "not signed in" or "wrong password"; they never mean a session has just ended.
const SIGN_IN_PATHS = new Set(['/auth/login', '/auth/session'])

/**
 * Calls the listener when the backend says the session has ended (a 401 on any other request): it was signed out
 * elsewhere, went idle, or the account was disabled. Returns a function that stops listening.
 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)

  return () => {
    unauthorizedListeners.delete(listener)
  }
}

// A 401 ends the session in the page. Two cases are left alone on purpose: a failed login or session check, which is
// an ordinary answer, and a 403, which means "signed in but not allowed" and must not sign anyone out.
apiClient.interceptors.response.use(undefined, (error: unknown) => {
  if (isAxiosError(error) && error.response?.status === 401 && !SIGN_IN_PATHS.has(error.config?.url ?? '')) {
    unauthorizedListeners.forEach((listener) => listener())
  }

  return Promise.reject(error)
})
