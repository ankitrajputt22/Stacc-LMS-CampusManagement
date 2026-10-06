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
