import { hasAnyRole, type AccountRole } from '../../auth/roles'
import type { NavIconName } from './navIcons'

export type NavigationItem = {
  label: string
  /** An item without a route is an upcoming feature. It is shown as "Soon" and is not a link. */
  to?: string
  /** Shown to accounts with any of these roles. An item without roles is shown to everyone who sees its section. */
  roles?: readonly AccountRole[]
  /** A count shown beside the label, such as the unread notifications of the Inbox. */
  badge?: 'unreadNotifications'
  /** The line icon beside the label. */
  icon: NavIconName
}

export type NavigationSection = {
  id: string
  /** The group's small uppercase label in the sidebar. A section without a title shows its items directly. */
  title?: string
  /** A page of the group itself, such as the ERP home: the title is then a link as well as a toggle. */
  to?: string
  /** Shown to accounts with any of these roles. A section without roles is shown to everyone. */
  roles?: readonly AccountRole[]
  items: readonly NavigationItem[]
}

const MEMBERS: readonly AccountRole[] = ['STUDENT', 'FACULTY', 'ADMIN']

// The one list of navigation for the whole app, on every screen size: the sidebar on wide screens and the menu drawer
// on phones. Sections appear in this order whatever order the roles arrive in. Each section is listed once and only
// filtered, so an account with several roles sees each section once and the shared items are never repeated.
//
// Showing or hiding an item here is for convenience only. It protects nothing: every feature is checked by the backend.
const navigationSections: readonly NavigationSection[] = [
  {
    id: 'home',
    items: [{ label: 'Overview', to: '/', icon: 'home' }],
  },
  {
    // The LMS side of a student's work.
    id: 'learning',
    title: 'Learning',
    roles: ['STUDENT'],
    items: [
      { label: 'Courses', to: '/courses', icon: 'courses' },
      // One AI study page across courses (v0.15.8); each course has its own study area.
      { label: 'AI Study', to: '/study', icon: 'ai' },
    ],
  },
  {
    id: 'teaching',
    title: 'Teaching',
    roles: ['FACULTY'],
    items: [
      { label: 'My Courses', to: '/teaching', icon: 'courses' },
      { label: 'Assignments', to: '/assignments', icon: 'assignments' },
      { label: 'Attendance', to: '/attendance', icon: 'attendance' },
      { label: 'Marks Entry', to: '/results', icon: 'exams' },
      // Who is registered in each offering taught; read-only.
      { label: 'Class Lists', to: '/class-lists', icon: 'clubs' },
      // Counts for the offerings taught; the backend decides access with FACULTY_REPORT_VIEW.
      { label: 'Reports', to: '/reports', icon: 'reports' },
    ],
  },
  {
    // Official records and student services (v0.16): one section beside the LMS, never a second login.
    id: 'erp',
    title: 'ERP',
    to: '/erp',
    roles: MEMBERS,
    items: [
      { label: 'My Profile', to: '/erp/profile', icon: 'profile' },
      { label: 'Academics', to: '/erp/academics', roles: ['STUDENT'], icon: 'academics' },
      { label: 'Timetable', to: '/erp/timetable', roles: ['FACULTY'], icon: 'calendar' },
      { label: 'Attendance', to: '/erp/attendance', roles: ['STUDENT'], icon: 'attendance' },
      { label: 'Exams & Results', to: '/erp/exams', roles: ['STUDENT'], icon: 'exams' },
    ],
  },
  {
    id: 'manage',
    title: 'Manage',
    roles: ['ADMIN'],
    items: [
      { label: 'Academic Admin', to: '/admin/academic', icon: 'academics' },
      { label: 'Users', to: '/admin/users', icon: 'clubs' },
      // Medical leave, attendance corrections, and revaluations; the backend checks ERP_REQUEST_MANAGE.
      { label: 'Requests', to: '/erp/requests', icon: 'assignments' },
      { label: 'Hostel Management', to: '/admin/hostel', icon: 'hostel' },
      { label: 'Campus Management', to: '/admin/campus', icon: 'campus' },
      // Categories and committees only; cases stay with their committees.
      { label: 'Grievance Committees', to: '/admin/grievances', icon: 'grievances' },
      { label: 'Mentoring', to: '/admin/mentoring', icon: 'mentoring' },
      { label: 'Certificates', to: '/admin/certificates', icon: 'documents' },
      { label: 'Reports', to: '/reports', icon: 'reports' },
      // Each part's status, the schema version, and optional features; the backend checks SYSTEM_HEALTH_VIEW.
      { label: 'System Health', to: '/admin/system', icon: 'health' },
      // Read-only record of sensitive changes; the backend checks AUDIT_LOG_VIEW.
      { label: 'Audit Log', to: '/admin/audit', icon: 'audit' },
    ],
  },
  {
    id: 'campus',
    title: 'Campus',
    items: [
      // Hostel applications and allocation.
      { label: 'Hostel', to: '/hostel', roles: ['STUDENT'], icon: 'hostel' },
      // A warden's own hostels: the backend lists only those they are assigned to.
      { label: 'Warden', to: '/warden', roles: ['WARDEN'], icon: 'hostel' },
      { label: 'Clubs & Events', to: '/campus/clubs', roles: MEMBERS, icon: 'clubs' },
      // One Campus page per account: services and requests for students and faculty, notices for every role.
      { label: 'Campus Services', to: '/campus', roles: MEMBERS, icon: 'campus' },
      // The catalog and loans for members; the lending desk for librarians.
      { label: 'Library', to: '/library', roles: ['STUDENT', 'FACULTY', 'LIBRARIAN'], icon: 'library' },
      { label: 'Projects', to: '/projects', roles: ['STUDENT', 'FACULTY'], icon: 'projects' },
      { label: 'Innovation', to: '/innovation', roles: MEMBERS, icon: 'ai' },
      { label: 'Hackathons', to: '/hackathons', roles: MEMBERS, icon: 'projects' },
      // The security desk: pass checks and the gate's movement log.
      { label: 'Gate', to: '/gate', roles: ['SECURITY'], icon: 'idCard' },
    ],
  },
  {
    id: 'services',
    title: 'Services',
    items: [
      // One To-Do list per account, worked out by the backend from every role the account holds.
      { label: 'To-Do', to: '/todo', roles: MEMBERS, icon: 'todo' },
      { label: 'Documents', to: '/documents', roles: ['STUDENT', 'FACULTY'], icon: 'documents' },
      // One Support area per account: tickets belong to accounts, and admins manage the queue from the same place.
      { label: 'Support', to: '/support', roles: MEMBERS, icon: 'support' },
      // Staff who work support tickets see the tickets routed to their teams; the backend checks each team.
      { label: 'Team Tickets', to: '/support/team', roles: ['MAINTENANCE', 'WARDEN', 'LIBRARIAN'], icon: 'support' },
      // Raising grievances (students, faculty) and committee cases (faculty, admins on a committee).
      { label: 'Grievances', to: '/grievances', roles: MEMBERS, icon: 'grievances' },
      // Mentors and their students; the backend opens only one's own pairings.
      { label: 'Mentoring', to: '/mentoring', roles: ['STUDENT', 'FACULTY'], icon: 'mentoring' },
      { label: 'Portfolio', to: '/portfolio', roles: ['STUDENT', 'FACULTY'], icon: 'portfolio' },
      // One digital ID per account, whatever roles it holds.
      { label: 'ID Card', to: '/id-card', roles: ['STUDENT', 'FACULTY', 'ADMIN', 'HOD', 'WARDEN', 'SECURITY', 'MAINTENANCE', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'idCard' },
      { label: 'Check an ID', to: '/verify-id', roles: ['ADMIN', 'SECURITY', 'LIBRARIAN', 'WARDEN'], icon: 'idCard' },
      // A head of department's figures for the departments they head now.
      { label: 'Department', to: '/department', roles: ['HOD'], icon: 'reports' },
      // Counts for the campus areas each account runs; the backend includes only those areas.
      { label: 'Area Analytics', to: '/areas', roles: ['ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'reports' },
      // The Inbox stays here for staff roles; members reach it from the top bar too.
      { label: 'Inbox', to: '/inbox', roles: ['MAINTENANCE', 'WARDEN', 'LIBRARIAN'], badge: 'unreadNotifications', icon: 'inbox' },
    ],
  },
  {
    id: 'career',
    items: [
      { label: 'Career', to: '/career', roles: ['STUDENT'], icon: 'career' },
      { label: 'Placement Office', to: '/placement', roles: ['ADMIN', 'PLACEMENT_OFFICER'], icon: 'career' },
    ],
  },
]

/**
 * The sections to show for an account with these roles, each with the items meant for it. Sections left without items
 * are dropped, so no empty group appears.
 */
export function navigationFor(roles: readonly AccountRole[]): NavigationSection[] {
  return navigationSections
    .filter((section) => section.roles === undefined || hasAnyRole(roles, section.roles))
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles === undefined || hasAnyRole(roles, item.roles)),
    }))
    .filter((section) => section.items.length > 0)
}

/** Whether a page belongs to this route: the route itself or a page under it. The Overview matches only itself. */
export function routeMatches(to: string, pathname: string): boolean {
  if (to === '/') {
    return pathname === '/'
  }
  return pathname === to || pathname.startsWith(`${to}/`)
}

/**
 * The trail shown in the top bar for a page, such as ["ERP", "My Profile"]: the group and the item whose route matches
 * the page most closely. Pages outside the list get an empty trail.
 */
export function breadcrumbFor(roles: readonly AccountRole[], pathname: string): string[] {
  let best: { trail: string[]; length: number } | null = null
  for (const section of navigationFor(roles)) {
    if (section.to && routeMatches(section.to, pathname) && (!best || section.to.length > best.length)) {
      best = { trail: [section.title ?? '', section.title ?? ''], length: section.to.length }
    }
    for (const item of section.items) {
      if (item.to && routeMatches(item.to, pathname) && (!best || item.to.length > best.length)) {
        best = { trail: section.title ? [section.title, item.label] : [item.label], length: item.to.length }
      }
    }
  }
  if (!best) {
    return []
  }
  const [first, second] = best.trail
  return second === undefined || second === first ? [first] : [first, second]
}
