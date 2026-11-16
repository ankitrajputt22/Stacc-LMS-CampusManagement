import { type AccountRole, hasAnyRole } from '../auth/roles'

/**
 * Where a sign-in leads when no page was asked for. Everyone lands on the Overview; an old LMS link
 * (`/login?portal=lms`) still opens Courses for an account that only studies and Teaching for one that only teaches.
 * It is never a role and grants nothing.
 */
export function landingFor(portal: string | null, roles: readonly AccountRole[]): string {
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
