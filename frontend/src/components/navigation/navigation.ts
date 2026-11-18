import { hasAnyRole, type AccountRole } from '../../auth/roles'
import type { NavIconName } from './navIcons'

export type NavigationItem = {
  label: string
  to?: string
  roles?: readonly AccountRole[]
  badge?: 'unreadNotifications'
  icon: NavIconName
}

export type NavigationSection = {
  id: string
  title?: string
  to?: string
  roles?: readonly AccountRole[]
  items: readonly NavigationItem[]
}

const MEMBERS: readonly AccountRole[] = ['STUDENT', 'FACULTY', 'ADMIN']

const navigationSections: readonly NavigationSection[] = [
  {
    id: 'home',
    items: [{ label: 'Overview', to: '/', icon: 'home' }],
  },
  {
    id: 'learning',
    title: 'Learning',
    roles: ['STUDENT'],
    items: [
      { label: 'Courses', to: '/courses', icon: 'courses' },
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
      { label: 'Marks Entry', to: '/erp/marks', icon: 'exams' },
      { label: 'Class Lists', to: '/class-lists', icon: 'clubs' },
      { label: 'Reports', to: '/reports', icon: 'reports' },
    ],
  },
  {
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
      { label: 'Fees', to: '/erp/fees', roles: ['STUDENT'], icon: 'fees' },
    ],
  },
  {
    id: 'manage',
    title: 'Manage',
    roles: ['ADMIN'],
    items: [
      { label: 'Academic Admin', to: '/admin/academic', icon: 'academics' },
      { label: 'Users', to: '/admin/users', icon: 'clubs' },
      { label: 'Exams', to: '/erp/exam-admin', icon: 'calendar' },
      { label: 'Fees Management', to: '/erp/fees-admin', icon: 'fees' },
      { label: 'Requests', to: '/erp/requests', icon: 'assignments' },
      { label: 'Hostel Management', to: '/admin/hostel', icon: 'hostel' },
      { label: 'Campus Management', to: '/admin/campus', icon: 'campus' },
      { label: 'Grievance Committees', to: '/admin/grievances', icon: 'grievances' },
      { label: 'Mentoring', to: '/admin/mentoring', icon: 'mentoring' },
      { label: 'Certificates', to: '/admin/certificates', icon: 'documents' },
      { label: 'Reports', to: '/reports', icon: 'reports' },
      { label: 'System Health', to: '/admin/system', icon: 'health' },
      { label: 'Audit Log', to: '/admin/audit', icon: 'audit' },
    ],
  },
  {
    id: 'campus',
    title: 'Campus',
    items: [
      { label: 'Hostel', to: '/hostel', roles: ['STUDENT'], icon: 'hostel' },
      { label: 'Warden', to: '/warden', roles: ['WARDEN'], icon: 'hostel' },
      { label: 'Clubs & Events', to: '/campus/clubs', roles: MEMBERS, icon: 'clubs' },
      { label: 'Campus Services', to: '/campus', roles: MEMBERS, icon: 'campus' },
      { label: 'Library', to: '/library', roles: ['STUDENT', 'FACULTY', 'LIBRARIAN'], icon: 'library' },
      { label: 'Projects', to: '/projects', roles: ['STUDENT', 'FACULTY'], icon: 'projects' },
      { label: 'Innovation', to: '/innovation', roles: MEMBERS, icon: 'ai' },
      { label: 'Hackathons', to: '/hackathons', roles: MEMBERS, icon: 'projects' },
      { label: 'Gate', to: '/gate', roles: ['SECURITY'], icon: 'idCard' },
    ],
  },
  {
    id: 'services',
    title: 'Services',
    items: [
      { label: 'To-Do', to: '/todo', roles: MEMBERS, icon: 'todo' },
      { label: 'Documents', to: '/documents', roles: ['STUDENT', 'FACULTY'], icon: 'documents' },
      { label: 'Support', to: '/support', roles: MEMBERS, icon: 'support' },
      { label: 'Team Tickets', to: '/support/team', roles: ['MAINTENANCE', 'WARDEN', 'LIBRARIAN'], icon: 'support' },
      { label: 'Grievances', to: '/grievances', roles: MEMBERS, icon: 'grievances' },
      { label: 'Mentoring', to: '/mentoring', roles: ['STUDENT', 'FACULTY'], icon: 'mentoring' },
      { label: 'Portfolio', to: '/portfolio', roles: ['STUDENT', 'FACULTY'], icon: 'portfolio' },
      { label: 'ID Card', to: '/id-card', roles: ['STUDENT', 'FACULTY', 'ADMIN', 'HOD', 'WARDEN', 'SECURITY', 'MAINTENANCE', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'idCard' },
      { label: 'Check an ID', to: '/verify-id', roles: ['ADMIN', 'SECURITY', 'LIBRARIAN', 'WARDEN'], icon: 'idCard' },
      { label: 'Department', to: '/department', roles: ['HOD'], icon: 'reports' },
      { label: 'Area Analytics', to: '/areas', roles: ['ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER'], icon: 'reports' },
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

export function navigationFor(roles: readonly AccountRole[]): NavigationSection[] {
  return navigationSections
    .filter((section) => section.roles === undefined || hasAnyRole(roles, section.roles))
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles === undefined || hasAnyRole(roles, item.roles)),
    }))
    .filter((section) => section.items.length > 0)
}

export function routeMatches(to: string, pathname: string): boolean {
  if (to === '/') {
    return pathname === '/'
  }
  return pathname === to || pathname.startsWith(`${to}/`)
}

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
