import axios, { isAxiosError } from 'axios'

const REQUEST_TIMEOUT_MS = 15000

export const API_VERSION = 'v1'

const apiRoot = import.meta.env.VITE_API_BASE_URL

if (!apiRoot) {
  throw new Error(
    'VITE_API_BASE_URL is not set. Copy frontend/.env.example to frontend/.env.',
  )
}

export const apiBaseUrl = `${apiRoot.replace(/\/+$/, '')}/${API_VERSION}`

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

const SIGN_IN_PATHS = new Set(['/auth/login', '/auth/session'])

export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)

  return () => {
    unauthorizedListeners.delete(listener)
  }
}

apiClient.interceptors.response.use(undefined, (error: unknown) => {
  if (isAxiosError(error) && error.response?.status === 401 && !SIGN_IN_PATHS.has(error.config?.url ?? '')) {
    unauthorizedListeners.forEach((listener) => listener())
  }

  return Promise.reject(error)
})
