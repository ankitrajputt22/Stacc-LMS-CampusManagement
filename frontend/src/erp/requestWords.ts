import { getApiError, getHttpStatus } from '../api/apiError'
import type { BadgeTone } from '../ui'
import type { RequestKind, RequestStatus } from '../types/erp'

export const KIND_WORDS: Record<RequestKind, string> = {
  MEDICAL_LEAVE: 'Medical leave',
  ATTENDANCE_CORRECTION: 'Attendance correction',
  REVALUATION: 'Revaluation',
}

export const STATUS_WORDS: Record<RequestStatus, string> = {
  SUBMITTED: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

export const STATUS_TONES: Record<RequestStatus, BadgeTone> = {
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}

/** A date such as "Tue 1 Dec 2026", from "2026-12-01" (a day, with no time zone involved). */
export function formatDay(day: string): string {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, date)).toLocaleDateString(undefined, {
    weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
  })
}

/** A moment shown as its day in the browser's time zone, such as "Thu 7 Aug 2026". */
export function formatMomentDay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
}

/** The words to show when a request could not be sent; null when the session ended. */
export function refusalOf(failure: unknown): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 400 || status === 404 || status === 409) {
    return getApiError(failure)?.message ?? 'That could not be sent. Check the details and try again.'
  }
  if (status === 403) {
    return 'You don’t have access to do this.'
  }
  return 'Couldn’t reach Stacc. Check your connection and try again.'
}
