import { hasAnyRole, type AccountRole } from '../../auth/roles'
import type { RailIconName } from './icons'

export type NavigationItem = {
  label: string
  /** An item without a route is an upcoming feature. It is shown as "Soon" and is not a link. */
  to?: string
  /** Shown to accounts with any of these roles. An item without roles is shown to everyone who sees its section. */
  roles?: readonly AccountRole[]
  /** A count shown beside the label, such as the unread notifications of the Inbox. */
  badge?: 'unreadNotifications'
  /** The icon beside the label in the navigation rail. */
  icon?: RailIconName
}

export type NavigationSection = {
  id: string
  title?: string
  /** Shown to accounts with any of these roles. A section without roles is shown to everyone. */
  roles?: readonly AccountRole[]
  /** The rail shows a titled section as one button with this icon, opening a panel of its items. */
  icon?: RailIconName
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
    items: [{ label: 'Dashboard', to: '/', icon: 'dashboard' }],
  },
  {
    id: 'student',
    title: 'Student',
    roles: ['STUDENT'],
    icon: 'student',
    items: [
      { label: 'Courses', to: '/courses' },
      // The student's academic records: one real link each, no separate menu.
      { label: 'Attendance', to: '/academic/attendance' },
      { label: 'Results', to: '/academic/results' },
      // Hostel applications and allocation.
      { label: 'Hostel', to: '/hostel' },
    ],
  },
  {
    id: 'faculty',
    title: 'Faculty',
    roles: ['FACULTY'],
    icon: 'faculty',
    items: [
      { label: 'Teaching', to: '/teaching' },
      { label: 'Assignments', to: '/assignments' },
      { label: 'Attendance', to: '/attendance' },
      { label: 'Results', to: '/results' },
      // Counts for the offerings taught; the backend decides access with FACULTY_REPORT_VIEW.
      { label: 'Reports', to: '/reports' },
    ],
  },
  {
    id: 'admin',
    title: 'Admin',
    roles: ['ADMIN'],
    icon: 'admin',
    items: [
      { label: 'Academic Admin', to: '/admin/academic' },
      { label: 'Hostel Management', to: '/admin/hostel' },
      { label: 'Campus Management', to: '/admin/campus' },
      { label: 'Users' },
      { label: 'Reports', to: '/reports' },
    ],
  },
  {
    id: 'common',
    title: 'Common',
    items: [
      // One calendar for every role: the backend decides which events and deadlines each account gets.
      { label: 'Calendar', to: '/calendar', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'calendar' },
      // One Inbox per account, whatever roles it holds: notifications are sent to accounts, not to roles.
      { label: 'Inbox', to: '/inbox', roles: ['STUDENT', 'FACULTY', 'ADMIN'], badge: 'unreadNotifications', icon: 'inbox' },
      // One Support area per account: tickets belong to accounts, and admins manage the queue from the same place.
      { label: 'Support', to: '/support', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'support' },
      // One Campus page per account: services and requests for students and faculty, notices for every role.
      { label: 'Campus', to: '/campus', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'campus' },
      // One search page for every account; the backend decides what each account may find.
      { label: 'Search', to: '/search', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'search' },
    ],
  },
]

/**
 * The sections to show for an account with these roles, each with the items meant for it. No roles gives only the
 * shared sections and their shared items.
 */
export function navigationFor(roles: readonly AccountRole[]): NavigationSection[] {
  return navigationSections
    .filter((section) => section.roles === undefined || hasAnyRole(roles, section.roles))
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles === undefined || hasAnyRole(roles, item.roles)),
    }))
}
