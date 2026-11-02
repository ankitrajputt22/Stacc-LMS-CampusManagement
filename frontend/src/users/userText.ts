import type { BadgeTone } from '../ui'
import type { AccountStatus, RoleName } from '../types/users'

export const USERS_DENIED = 'You don’t have access to user management.'
export const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again.'
export const ROLES: RoleName[] = ['STUDENT', 'FACULTY', 'ADMIN']

const ROLE_WORDS: Record<RoleName, string> = { STUDENT: 'Student', FACULTY: 'Faculty', ADMIN: 'Admin' }

export const roleWord = (role: RoleName) => ROLE_WORDS[role] ?? role
export const statusWord = (status: AccountStatus) => (status === 'ACTIVE' ? 'Active' : 'Disabled')
export const statusTone = (status: AccountStatus): BadgeTone => (status === 'ACTIVE' ? 'success' : 'neutral')

/** A refused change in safe words: the backend's message for 400, 404, and 409; never a raw error. */
export function refusal(status: number | null, apiMessage: string | null): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? 'That could not be saved. Check the values and try again.'
    case 403:
      return `${USERS_DENIED} ${SIGN_IN_AGAIN}`
    default:
      return 'Couldn’t reach Stacc. Check your connection and try again.'
  }
}
