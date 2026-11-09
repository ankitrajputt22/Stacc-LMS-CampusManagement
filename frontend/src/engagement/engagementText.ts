import { getApiError, getHttpStatus } from '../api/apiError'

/** A refused change in safe words; null for a 401 (the session ends on its own). */
export function failureText(error: unknown, fallback: string): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  if (status === 403) {
    return 'You don’t have access to do this.'
  }
  const message = getApiError(error)?.message
  return message && status !== null && status < 500 ? message : fallback
}

export const POLICY_LABEL = { OPEN: 'Open to join', APPROVAL: 'Join on approval', CLOSED: 'Not taking members' } as const
export const TITLE_LABEL = { ADVISOR: 'Faculty advisor', COACH: 'Coach', PRESIDENT: 'President', SECRETARY: 'Secretary', OFFICER: 'Officer' } as const
export const BOOKING_LABEL = { CONFIRMED: 'Confirmed', PENDING: 'Waiting for approval', REJECTED: 'Refused', CANCELLED: 'Cancelled' } as const
export const EVENT_STATUS_LABEL = { DRAFT: 'Draft', PENDING_APPROVAL: 'Waiting for the advisor', PUBLISHED: 'Published', CANCELLED: 'Cancelled' } as const

/** A datetime-local value (browser time) as an ISO moment, or null. */
export function localToIso(value: string): string | null {
  if (value === '') {
    return null
  }
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? null : time.toISOString()
}
