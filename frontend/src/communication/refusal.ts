import { getApiError, getHttpStatus } from '../api/apiError'

export function refusalText(failure: unknown, fallback: string): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return 'You don’t have access to do this. If your access changed recently, sign out and sign in again.'
  }
  const message = getApiError(failure)?.message
  return message && status !== null && status < 500 ? message : fallback
}
