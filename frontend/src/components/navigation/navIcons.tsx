import type { ReactNode } from 'react'

/** The line icons of the sidebar and top bar. Each sits beside visible words, so they are hidden from screen readers. */
export type NavIconName =
  | 'home'
  | 'courses'
  | 'ai'
  | 'profile'
  | 'academics'
  | 'attendance'
  | 'exams'
  | 'fees'
  | 'hostel'
  | 'clubs'
  | 'campus'
  | 'documents'
  | 'support'
  | 'grievances'
  | 'mentoring'
  | 'portfolio'
  | 'career'
  | 'assignments'
  | 'reports'
  | 'health'
  | 'audit'
  | 'library'
  | 'projects'
  | 'idCard'
  | 'todo'
  | 'inbox'
  | 'calendar'
  | 'search'
  | 'chevronDown'
  | 'chevronRight'
  | 'menu'
  | 'close'
  | 'phone'
  | 'pin'
  | 'info'
  | 'warning'

const PATHS: Record<NavIconName, ReactNode> = {
  home: <path d="M3 10.5 12 3l9 7.5M5 9v11h5v-6h4v6h5V9" />,
  courses: (
    <>
      <path d="M2.5 9 12 4.5 21.5 9 12 13.5z" />
      <path d="M6.5 11v4.5c1.5 1.5 3.4 2.2 5.5 2.2s4-.7 5.5-2.2V11M21.5 9v5" />
    </>
  ),
  ai: <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8zM18.5 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" />,
  profile: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5" />
    </>
  ),
  academics: <path d="M12 3 2.5 8 12 13l9.5-5zM2.5 12.5 12 17.5l9.5-5M2.5 16.5 12 21.5l9.5-5" />,
  attendance: (
    <>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
      <path d="M3.5 10h17M8 3v4M16 3v4M9 15l2 2 4-4" />
    </>
  ),
  exams: (
    <>
      <path d="M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z" />
      <path d="M14 3v5h5M8.5 13h7M8.5 17h5" />
    </>
  ),
  fees: (
    <>
      <rect x="2.5" y="6" width="19" height="13" rx="2" />
      <path d="M2.5 10.5h19M6.5 15h3" />
    </>
  ),
  hostel: <path d="M3 19V6M3 15h18v4M3 11h7a2 2 0 0 1 2 2v2M12 11h6.5a2.5 2.5 0 0 1 2.5 2.5V15M6.5 10.8a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4z" />,
  clubs: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c.8-3.2 3.3-5 6.5-5s5.7 1.8 6.5 5M16 4.6a3.5 3.5 0 0 1 0 6.8M18.5 15.3c1.6.8 2.6 2.4 3 4.7" />
    </>
  ),
  campus: <path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11zM12 12.4a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z" />,
  documents: (
    <>
      <path d="M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z" />
      <path d="M14 3v5h5M8.5 12.5h7M8.5 16h7" />
    </>
  ),
  support: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.6 2.6 0 1 1 3.8 2.3c-.8.4-1.3 1-1.3 1.9v.3M12 17.2h0" />
    </>
  ),
  grievances: <path d="M12 3 4.5 6v5.5c0 4.6 3.1 8.2 7.5 9.5 4.4-1.3 7.5-4.9 7.5-9.5V6zM12 8v4.5M12 16h0" />,
  mentoring: (
    <>
      <circle cx="12" cy="7.5" r="3.5" />
      <path d="M5 20.5c.9-3.7 3.6-6 7-6s6.1 2.3 7 6" />
    </>
  ),
  portfolio: (
    <>
      <rect x="3.5" y="3.5" width="17" height="17" rx="2" />
      <path d="M8 15.5l3-3.5 2.5 2.5 2.5-3.5M8 8h4" />
    </>
  ),
  career: (
    <>
      <rect x="2.5" y="7" width="19" height="13" rx="2" />
      <path d="M8.5 7V5a1.5 1.5 0 0 1 1.5-1.5h4A1.5 1.5 0 0 1 15.5 5v2M2.5 12.5h19" />
    </>
  ),
  assignments: (
    <>
      <rect x="5" y="4" width="14" height="17" rx="1.5" />
      <path d="M9 3h6v3H9zM8.5 11l1.5 1.5 3-3M8.5 16.5h7" />
    </>
  ),
  reports: <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />,
  health: <path d="M3 12h4l2.5-6 4 12 2.5-6H21" />,
  audit: (
    <>
      <path d="M8 3.5h8M7 6.5h10a1.5 1.5 0 0 1 1.5 1.5v11.5A1.5 1.5 0 0 1 17 21H7a1.5 1.5 0 0 1-1.5-1.5V8A1.5 1.5 0 0 1 7 6.5z" />
      <path d="M9 12h6M9 16h4" />
    </>
  ),
  library: <path d="M4 19.5V4.5A1.5 1.5 0 0 1 5.5 3H11v17H5.5A1.5 1.5 0 0 1 4 18.5M20 19.5V4.5A1.5 1.5 0 0 0 18.5 3H13v17h5.5a1.5 1.5 0 0 0 1.5-1.5" />,
  projects: <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H10l2 2.5h7.5A1.5 1.5 0 0 1 21 10v8.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z" />,
  idCard: (
    <>
      <rect x="2.5" y="5" width="19" height="14" rx="2" />
      <circle cx="8.5" cy="11" r="2.2" />
      <path d="M5.5 16c.6-1.5 1.7-2.3 3-2.3s2.4.8 3 2.3M14 10h4.5M14 13.5h3" />
    </>
  ),
  todo: <path d="M4 6.5l1.5 1.5L8 5.5M4 12.5l1.5 1.5L8 11.5M4 18.5l1.5 1.5L8 17.5M11 7h9M11 13h9M11 19h9" />,
  inbox: (
    <>
      <rect x="2.5" y="5" width="19" height="14" rx="2" />
      <path d="m3 6.5 9 6.5 9-6.5" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </>
  ),
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M15.5 15.5 21 21" />
    </>
  ),
  chevronDown: <path d="m6 9 6 6 6-6" />,
  chevronRight: <path d="m9 6 6 6-6 6" />,
  menu: <path d="M3.5 6h17M3.5 12h17M3.5 18h17" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  phone: <path d="M5 3.5h3.5l1.5 4.5-2.2 1.5a11 11 0 0 0 6.7 6.7l1.5-2.2 4.5 1.5V19a1.5 1.5 0 0 1-1.6 1.5C10.4 20 4 13.6 3.5 5.1A1.5 1.5 0 0 1 5 3.5z" />,
  pin: <path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11zM12 12.4a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z" />,
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5.5M12 7.5h0" />
    </>
  ),
  warning: <path d="M12 3.5 2.5 20h19zM12 10v4.5M12 17.5h0" />,
}

export function NavIcon({ name, className = 'size-5' }: { name: NavIconName; className?: string }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`shrink-0 ${className}`}
    >
      {PATHS[name]}
    </svg>
  )
}
