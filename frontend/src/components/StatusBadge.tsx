import type { ReactNode } from 'react'

type StatusBadgeProps = {
  children: ReactNode
}

export function StatusBadge({ children }: StatusBadgeProps) {
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5 rounded-control border border-success-line bg-success-soft px-2 py-1 text-xs font-medium text-success">
      <span aria-hidden="true" className="size-1.5 rounded-full bg-success" />
      {children}
    </span>
  )
}
