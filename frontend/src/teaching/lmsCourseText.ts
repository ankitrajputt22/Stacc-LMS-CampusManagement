import type { LmsCourseLifecycleStatus } from '../types/lms'

export const MANAGE_DENIED = 'You don’t have access to manage this LMS course.'
export const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again.'

const STATUS_WORDS: Record<LmsCourseLifecycleStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
}

export function lmsStatusWord(status: LmsCourseLifecycleStatus | null): string {
  return status === null ? 'Not created' : STATUS_WORDS[status]
}

export function enrolledText(count: number | null): string {
  if (count === null) {
    return ''
  }
  return count === 1 ? '1 student is enrolled.' : `${count} students are enrolled.`
}

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
