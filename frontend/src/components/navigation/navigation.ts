import { hasAnyRole, type AccountRole } from '../../auth/roles'

export type NavigationItem = {
  label: string
  /** An item without a route is an upcoming feature. It is shown as "Soon" and is not a link. */
  to?: string
}

export type NavigationSection = {
  id: string
  title?: string
  /** Shown to accounts with any of these roles. A section without roles is shown to everyone. */
  roles?: readonly AccountRole[]
  items: readonly NavigationItem[]
}

// The one list of navigation for the whole app, on every screen size. Sections appear in this
// order whatever order the roles arrive in. Each section is listed once and only filtered, so an
// account with several roles sees each section once and the shared items are never repeated.
//
// Showing or hiding an item here is for convenience only. It protects nothing: every feature is
// checked by the backend.
const navigationSections: readonly NavigationSection[] = [
  {
    id: 'home',
    items: [{ label: 'Dashboard', to: '/' }],
  },
  {
    id: 'student',
    title: 'Student',
    roles: ['STUDENT'],
    items: [
      { label: 'Courses', to: '/courses' },
      // The student's academic records: one real link each, no separate menu.
      { label: 'Attendance', to: '/academic/attendance' },
      { label: 'Results', to: '/academic/results' },
      { label: 'Campus' },
    ],
  },
  {
    id: 'faculty',
    title: 'Faculty',
    roles: ['FACULTY'],
    items: [
      { label: 'Teaching', to: '/teaching' },
      { label: 'Assignments', to: '/assignments' },
      { label: 'Attendance', to: '/attendance' },
      { label: 'Results', to: '/results' },
    ],
  },
  {
    id: 'admin',
    title: 'Admin',
    roles: ['ADMIN'],
    items: [{ label: 'Academic Admin' }, { label: 'Users' }, { label: 'Reports' }],
  },
  {
    id: 'common',
    title: 'Common',
    items: [{ label: 'Calendar' }, { label: 'Inbox' }, { label: 'Support' }],
  },
]

/** The sections to show for an account with these roles. No roles gives only the shared sections. */
export function navigationFor(roles: readonly AccountRole[]): NavigationSection[] {
  return navigationSections.filter(
    (section) => section.roles === undefined || hasAnyRole(roles, section.roles),
  )
}
