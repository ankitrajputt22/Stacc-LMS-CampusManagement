export const ACCOUNT_ROLES = [
  'STUDENT',
  'FACULTY',
  'ADMIN',
  'HOD',
  'WARDEN',
  'SECURITY',
  'MAINTENANCE',
  'LIBRARIAN',
  'PLACEMENT_OFFICER',
] as const

export type AccountRole = (typeof ACCOUNT_ROLES)[number]

const ROLE_LABELS: Record<AccountRole, string> = {
  STUDENT: 'Student',
  FACULTY: 'Faculty',
  ADMIN: 'Admin',
  HOD: 'Head of Department',
  WARDEN: 'Warden',
  SECURITY: 'Security',
  MAINTENANCE: 'Maintenance',
  LIBRARIAN: 'Librarian',
  PLACEMENT_OFFICER: 'Placement Officer',
}

export function toAccountRoles(roles: readonly string[]): AccountRole[] {
  return ACCOUNT_ROLES.filter((role) => roles.includes(role))
}

export function roleLabel(role: AccountRole): string {
  return ROLE_LABELS[role]
}

export function hasAnyRole(
  roles: readonly AccountRole[],
  wanted: readonly AccountRole[],
): boolean {
  return wanted.some((role) => roles.includes(role))
}
