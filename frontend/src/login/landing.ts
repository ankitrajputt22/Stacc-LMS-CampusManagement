import { type AccountRole, hasAnyRole } from '../auth/roles'

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
