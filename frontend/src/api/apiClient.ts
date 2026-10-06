import axios from 'axios'

const REQUEST_TIMEOUT_MS = 15000

const baseURL = import.meta.env.VITE_API_BASE_URL

if (!baseURL) {
  throw new Error(
    'VITE_API_BASE_URL is not set. Copy frontend/.env.example to frontend/.env.',
  )
}

// Axios sends plain objects as JSON on its own, so no default Content-Type is set here.
export const apiClient = axios.create({
  baseURL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: {
    Accept: 'application/json',
  },
})
