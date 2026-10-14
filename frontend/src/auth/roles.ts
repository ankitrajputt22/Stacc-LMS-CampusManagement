/**
 * The roles the college can give an account, in the order they are always shown. The backend
 * sends these same plain names in the login answer (never with a ROLE_ prefix), so nothing is
 * stripped or renamed anywhere in the frontend.
 */
export const ACCOUNT_ROLES = ['STUDENT', 'FACULTY', 'ADMIN'] as const

export type AccountRole = (typeof ACCOUNT_ROLES)[number]

const ROLE_LABELS: Record<AccountRole, string> = {
  STUDENT: 'Student',
  FACULTY: 'Faculty',
  ADMIN: 'Admin',
}

/**
 * The account's roles that this app knows, without repeats, in the fixed order above. The order
 * the backend sends means nothing, and no role is the "main" one. Anything unknown is dropped,
 * so a strange value can never unlock a menu.
 */
export function toAccountRoles(roles: readonly string[]): AccountRole[] {
  return ACCOUNT_ROLES.filter((role) => roles.includes(role))
}

export function roleLabel(role: AccountRole): string {
  return ROLE_LABELS[role]
}

/**
 * Whether the interface should show something meant for any of these roles. This is never a
 * security check: the backend decides what every request may do.
 */
export function hasAnyRole(
  roles: readonly AccountRole[],
  wanted: readonly AccountRole[],
): boolean {
  return wanted.some((role) => roles.includes(role))
}
