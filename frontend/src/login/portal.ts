import { type AccountRole, hasAnyRole } from '../auth/roles'

/**
 * The two ways into Stacc from the sign-in page. Both use the same college account and the same sign-in; the choice
 * only decides where the visitor lands afterwards. It is not a role and grants nothing.
 */
export type Portal = 'erp' | 'lms'

export const PORTALS: readonly Portal[] = ['erp', 'lms']

export const PORTAL_NAMES: Record<Portal, string> = { erp: 'ERP', lms: 'LMS' }

export const PORTAL_SUMMARIES: Record<Portal, string> = {
  erp: 'Academic records, attendance, results, calendar, hostel, campus services, and support.',
  lms: 'Your courses, study materials, announcements, assignments, and teaching.',
}

/** The portal named in the address (`?portal=erp` or `?portal=lms`), or null for the welcome view. */
export function parsePortal(value: string | null): Portal | null {
  return value === 'erp' || value === 'lms' ? value : null
}

export function portalPath(portal: Portal): string {
  return `/login?portal=${portal}`
}

/**
 * Where a portal leads after signing in. ERP opens the dashboard. LMS opens Courses for an account that only studies
 * and Teaching for one that only teaches; an account holding both (or neither) gets the dashboard, which shows every
 * role's part, so no role is ever treated as the main one.
 */
export function portalHome(portal: Portal | null, roles: readonly AccountRole[]): string {
  if (portal !== 'lms') {
    return '/'
  }
  const student = hasAnyRole(roles, ['STUDENT'])
  const faculty = hasAnyRole(roles, ['FACULTY'])
  if (student && !faculty) {
    return '/courses'
  }
  if (faculty && !student) {
    return '/teaching'
  }
  return '/'
}
