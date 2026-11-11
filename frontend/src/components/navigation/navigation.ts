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
    // Each role's tray is named for what people do in it (roadmap decision 9): studying, teaching, managing.
    id: 'student',
    title: 'Academics',
    roles: ['STUDENT'],
    icon: 'student',
    items: [
      { label: 'Courses', to: '/courses' },
      // Choosing courses for the semester while registration is open.
      { label: 'Registration', to: '/academic/registration' },
      // The student's academic records: one real link each, no separate menu.
      { label: 'Attendance', to: '/academic/attendance' },
      { label: 'Results', to: '/academic/results' },
      { label: 'My progress', to: '/academic/analytics' },
      // Hostel applications and allocation.
      { label: 'Hostel', to: '/hostel' },
    ],
  },
  {
    id: 'faculty',
    title: 'Teaching',
    roles: ['FACULTY'],
    icon: 'faculty',
    items: [
      { label: 'Teaching', to: '/teaching' },
      { label: 'Assignments', to: '/assignments' },
      { label: 'Attendance', to: '/attendance' },
      { label: 'Results', to: '/results' },
      // Who is registered in each offering taught; read-only.
      { label: 'Class Lists', to: '/class-lists' },
      // Counts for the offerings taught; the backend decides access with FACULTY_REPORT_VIEW.
      { label: 'Reports', to: '/reports' },
    ],
  },
  {
    id: 'admin',
    title: 'Manage',
    roles: ['ADMIN'],
    icon: 'admin',
    items: [
      { label: 'Academic Admin', to: '/admin/academic' },
      { label: 'Hostel Management', to: '/admin/hostel' },
      { label: 'Campus Management', to: '/admin/campus' },
      { label: 'Users', to: '/admin/users' },
      // Categories and committees only; cases stay with their committees.
      { label: 'Grievance committees', to: '/admin/grievances' },
      { label: 'Mentoring', to: '/admin/mentoring' },
      // Each part's status, the schema version, and optional features; the backend checks SYSTEM_HEALTH_VIEW.
      { label: 'System health', to: '/admin/system' },
      // Read-only record of sensitive changes; the backend checks AUDIT_LOG_VIEW.
      { label: 'Audit log', to: '/admin/audit' },
      { label: 'Reports', to: '/reports' },
    ],
  },
  {
    id: 'common',
    title: 'Common',
    items: [
      // One calendar for every role: the backend decides which events and deadlines each account gets.
      { label: 'Calendar', to: '/calendar', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'calendar' },
      // One To-Do list per account, worked out by the backend from every role the account holds.
      { label: 'To-Do', to: '/todo', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'todo' },
      // One Inbox per account, whatever roles it holds: notifications are sent to accounts, not to roles.
      { label: 'Inbox', to: '/inbox', roles: ['STUDENT', 'FACULTY', 'ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN'], badge: 'unreadNotifications', icon: 'inbox' },
      // One Support area per account: tickets belong to accounts, and admins manage the queue from the same place.
      { label: 'Support', to: '/support', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'support' },
      // Staff who work support tickets see the tickets routed to their teams; the backend checks each team.
      { label: 'Team tickets', to: '/support/team', roles: ['MAINTENANCE', 'WARDEN', 'LIBRARIAN'], icon: 'support' },
      // A warden's own hostels: the backend lists only those they are assigned to.
      { label: 'Warden', to: '/warden', roles: ['WARDEN'], icon: 'campus' },
      // One digital ID per account, whatever roles it holds.
      { label: 'ID card', to: '/id-card', roles: ['STUDENT', 'FACULTY', 'ADMIN', 'HOD', 'WARDEN', 'SECURITY', 'MAINTENANCE', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'account' },
      { label: 'Check an ID', to: '/verify-id', roles: ['ADMIN', 'SECURITY', 'LIBRARIAN', 'WARDEN'], icon: 'account' },
      { label: 'Documents', to: '/documents', roles: ['STUDENT', 'FACULTY'], icon: 'inbox' },
      // Raising grievances (students, faculty) and committee cases (faculty, admins on a committee); the backend checks each case.
      { label: 'Grievances', to: '/grievances', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'support' },
      // Mentors and their students; the backend opens only one's own pairings.
      { label: 'Mentoring', to: '/mentoring', roles: ['STUDENT', 'FACULTY'], icon: 'student' },
      // A student's portfolio; faculty see the items they were asked to verify.
      { label: 'Portfolio', to: '/portfolio', roles: ['STUDENT', 'FACULTY'], icon: 'student' },
      // Student projects with join requests and faculty mentors.
      { label: 'Projects', to: '/projects', roles: ['STUDENT', 'FACULTY'], icon: 'student' },
      // Ideas: submitting, faculty reviews, and the campus office's decisions, one part per role held.
      { label: 'Innovation', to: '/innovation', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'student' },
      // Teams and submissions for students, judging for faculty, running them for admins.
      { label: 'Hackathons', to: '/hackathons', roles: ['STUDENT', 'FACULTY', 'ADMIN'], icon: 'student' },
      // The catalog and loans for members; the lending desk for librarians.
      { label: 'Library', to: '/library', roles: ['STUDENT', 'FACULTY', 'LIBRARIAN'], icon: 'student' },
      // A head of department's figures for the departments they head now.
      { label: 'Department', to: '/department', roles: ['HOD'], icon: 'admin' },
      // Counts for the campus areas each account runs; the backend includes only those areas.
      { label: 'Area analytics', to: '/areas', roles: ['ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'admin' },
      { label: 'Career', to: '/career', roles: ['STUDENT'], icon: 'student' },
      { label: 'Placement office', to: '/placement', roles: ['ADMIN', 'PLACEMENT_OFFICER'], icon: 'admin' },
      { label: 'Certificates', to: '/admin/certificates', roles: ['ADMIN'], icon: 'admin' },
      // The security desk: pass checks and the gate's movement log.
      { label: 'Gate', to: '/gate', roles: ['SECURITY'], icon: 'campus' },
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
