import type { LmsCourseLifecycleStatus } from '../types/lms'

export const MANAGE_DENIED = 'You don’t have access to manage this LMS course.'
export const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again.'

const STATUS_WORDS: Record<LmsCourseLifecycleStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
}

/** An LMS course's status as a word, or "Not created" when the offering has none. */
export function lmsStatusWord(status: LmsCourseLifecycleStatus | null): string {
  return status === null ? 'Not created' : STATUS_WORDS[status]
}

/** "Student access synchronized for 28 students.", with the count the backend sent. */
export function synchronizedText(count: number | null): string {
  if (count === null) {
    return 'Student access synchronized.'
  }
  return `Student access synchronized for ${count} ${count === 1 ? 'student' : 'students'}.`
}

/** A refused lifecycle change in safe words: the backend's message for a 400 or 409, never a raw error. */
export function lifecycleRefusal(status: number | null, apiMessage: string | null, fallback: string): string {
  switch (status) {
    case 400:
    case 409:
      return apiMessage ?? fallback
    case 403:
      return `${MANAGE_DENIED} ${SIGN_IN_AGAIN}`
    case 404:
      return 'Teaching course unavailable.'
    default:
      return fallback
  }
}
