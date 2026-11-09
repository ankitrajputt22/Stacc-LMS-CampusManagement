import type { ReactNode } from 'react'

/**
 * A plain bordered frame for a page state that stands in for content, so the page does not jump
 * when the content arrives.
 */
export function StatePanel({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-panel border border-line bg-surface">{children}</div>
  )
}
