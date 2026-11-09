import type { ReactNode } from 'react'

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
    >
      {children}
    </svg>
  )
}

export function MenuIcon() {
  return (
    <Icon>
      <path d="M3 5.5h14M3 10h14M3 14.5h14" />
    </Icon>
  )
}

export function CloseIcon() {
  return (
    <Icon>
      <path d="M5 5l10 10M15 5L5 15" />
    </Icon>
  )
}

export function BellIcon() {
  return (
    <Icon>
      <path d="M5 14V9a5 5 0 0 1 10 0v5l1.5 1.5h-13L5 14Z" />
      <path d="M8.5 17.5h3" />
    </Icon>
  )
}

export function UserIcon() {
  return (
    <Icon>
      <circle cx="10" cy="7" r="3" />
      <path d="M4 16.5a6 6 0 0 1 12 0" />
    </Icon>
  )
}

export function SearchIcon() {
  return (
    <Icon>
      <circle cx="8.5" cy="8.5" r="5" />
      <path d="M12.5 12.5l4 4" />
    </Icon>
  )
}

export type RailIconName =
  | 'dashboard'
  | 'student'
  | 'faculty'
  | 'admin'
  | 'calendar'
  | 'todo'
  | 'inbox'
  | 'support'
  | 'campus'
  | 'search'
  | 'account'

const RAIL_PATHS: Record<RailIconName, ReactNode> = {
  dashboard: (
    <>
      <path d="M4 15a8 8 0 1 1 16 0" />
      <path d="M12 15l4-5" />
      <path d="M3 19h18" />
    </>
  ),
  student: (
    <>
      <path d="M2.5 9 12 4.5 21.5 9 12 13.5z" />
      <path d="M6.5 11v4.5c1.5 1.5 3.4 2.2 5.5 2.2s4-.7 5.5-2.2V11" />
      <path d="M21.5 9v5" />
    </>
  ),
  faculty: (
    <>
      <rect x="3" y="4" width="18" height="12" rx="1" />
      <path d="M8 20l4-4 4 4" />
      <path d="M7 9h6M7 12h9" />
    </>
  ),
  admin: (
    <>
      <path d="M3 20h18" />
      <path d="M5 20V9l7-4.5L19 9v11" />
      <path d="M9 20v-6h6v6" />
      <path d="M10 10h4" />
    </>
  ),
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15" rx="1.5" />
      <path d="M3.5 9.5h17M8 3v4M16 3v4" />
      <path d="M8 13h2M12 13h2M16 13h0M8 16.5h2M12 16.5h2" />
    </>
  ),
  todo: (
    <>
      <path d="M4 6.5l1.5 1.5L8 5.5M4 12.5l1.5 1.5L8 11.5M4 18.5l1.5 1.5L8 17.5" />
      <path d="M11 7h9M11 13h9M11 19h9" />
    </>
  ),
  inbox: (
    <>
      <path d="M3.5 13 6 5h12l2.5 8v6h-17z" />
      <path d="M3.5 13H9l1 2h4l1-2h5.5" />
    </>
  ),
  support: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.6 2.6 0 1 1 3.8 2.3c-.8.4-1.3 1-1.3 1.9v.3" />
      <path d="M12 17.2h0" />
    </>
  ),
  campus: (
    <>
      <path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z" />
      <circle cx="12" cy="10" r="2.4" />
    </>
  ),
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6" />
      <path d="M15 15l5.5 5.5" />
    </>
  ),
  account: (
    <>
      <circle cx="12" cy="8.5" r="4" />
      <path d="M4.5 20c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5" />
    </>
  ),
}

/** The larger line icons of the navigation rail. Always beside a visible label, so hidden from screen readers. */
export function RailIcon({ name, className = 'size-7' }: { name: RailIconName; className?: string }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {RAIL_PATHS[name]}
    </svg>
  )
}
