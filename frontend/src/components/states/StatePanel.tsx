import type { ReactNode } from 'react'

export function StatePanel({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-panel border border-line bg-surface">{children}</div>
  )
}
